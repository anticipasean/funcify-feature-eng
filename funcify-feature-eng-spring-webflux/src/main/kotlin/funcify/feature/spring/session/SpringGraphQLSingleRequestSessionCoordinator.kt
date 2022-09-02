package funcify.feature.spring.session

import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import funcify.feature.materializer.request.GraphQLExecutionInputCustomizer
import funcify.feature.materializer.response.SerializedGraphQLResponse
import funcify.feature.materializer.response.SerializedGraphQLResponseFactory
import funcify.feature.materializer.service.MaterializationPreparsedDocumentProvider
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.materializer.session.GraphQLSingleRequestSessionCoordinator
import funcify.feature.spring.error.FeatureEngSpringWebFluxException
import funcify.feature.spring.error.SpringWebFluxErrorResponse
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.recurse
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.ExceptionWhileDataFetching
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import graphql.execution.ResultPath
import graphql.language.SourceLocation
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import org.slf4j.Logger
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2/19/22
 */
internal class SpringGraphQLSingleRequestSessionCoordinator(
    private val asyncExecutor: Executor,
    private val serializedGraphQLResponseFactory: SerializedGraphQLResponseFactory,
    private val materializationPreparsedDocumentProvider: MaterializationPreparsedDocumentProvider
) : GraphQLSingleRequestSessionCoordinator {

    companion object {
        private val logger: Logger = loggerFor<SpringGraphQLSingleRequestSessionCoordinator>()

        private object SpringGraphQLSingleRequestDataFetcherExceptionHandler :
            DataFetcherExceptionHandler {

            override fun handleException(
                handlerParameters: DataFetcherExceptionHandlerParameters
            ): CompletableFuture<DataFetcherExceptionHandlerResult> {
                val rootCause =
                    handlerParameters
                        .toOption()
                        .mapNotNull { hp -> hp.exception }
                        .recurse { x ->
                            when (x) {
                                is GraphQLError -> x.right().some()
                                else -> {
                                    when (val innerCause = x.cause) {
                                        null -> x.right().some()
                                        else -> innerCause.left().some()
                                    }
                                }
                            }
                        }
                val sourceLocation =
                    handlerParameters.toOption().mapNotNull { hp -> hp.sourceLocation }
                val path = handlerParameters.toOption().mapNotNull { hp -> hp.path }
                return CompletableFuture.completedFuture(
                    DataFetcherExceptionHandlerResult.newResult(
                            ExceptionWhileDataFetching(
                                path.getOrElse { ResultPath.rootPath() },
                                rootCause.getOrElse {
                                    FeatureEngSpringWebFluxException(
                                        SpringWebFluxErrorResponse.EXECUTION_RESULT_ISSUE,
                                        "error reported during data_fetching that could not be unwrapped properly"
                                    )
                                },
                                sourceLocation.getOrElse { SourceLocation.EMPTY }
                            )
                        )
                        .build()
                )
            }
        }
    }

    override fun conductSingleRequestSession(
        session: GraphQLSingleRequestSession
    ): Mono<GraphQLSingleRequestSession> {
        logger.info(
            """conduct_single_request_session: [ 
                |session.session_id: ${session.sessionId} ]
                |""".flatten()
        )
        return Mono.fromCompletionStage(
                CompletableFuture.supplyAsync(
                        { ->
                            GraphQL.newGraphQL(session.materializationSchema)
                                .preparsedDocumentProvider(materializationPreparsedDocumentProvider)
                                .defaultDataFetcherExceptionHandler(
                                    SpringGraphQLSingleRequestDataFetcherExceptionHandler
                                )
                                .build()
                                .executeAsync(executionInputBuilderUpdater(session))
                        },
                        asyncExecutor
                    )
                    .thenComposeAsync { cs -> cs }
            )
            .map { er: ExecutionResult ->
                serializedGraphQLResponseFactory.builder().executionResult(er).build()
            }
            .map { sgqlr: SerializedGraphQLResponse ->
                session.update { serializedGraphQLResponse(sgqlr) }
            }
    }

    private fun executionInputBuilderUpdater(
        session: GraphQLSingleRequestSession
    ): (ExecutionInput.Builder) -> ExecutionInput.Builder {
        return { builder: ExecutionInput.Builder ->
            session.rawGraphQLRequest.executionInputCustomizers.fold(
                builder
                    .executionId(session.rawGraphQLRequest.executionId)
                    .locale(session.rawGraphQLRequest.locale)
                    .operationName(session.rawGraphQLRequest.operationName)
                    .query(session.rawGraphQLRequest.rawGraphQLQueryText)
                    .variables(session.rawGraphQLRequest.variables)
                    .graphQLContext { ctxBuilder ->
                        ctxBuilder.put(
                            GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY,
                            session
                        )
                    }
            ) { bldr: ExecutionInput.Builder, customizer: GraphQLExecutionInputCustomizer ->
                customizer.invoke(bldr)
            }
        }
    }
}
