package funcify.feature.materializer.session

import arrow.core.getOrElse
import arrow.core.toOption
import funcify.feature.error.ServiceError
import funcify.feature.materializer.document.MaterializationPreparsedDocumentProvider
import funcify.feature.materializer.request.GraphQLExecutionInputCustomizer
import funcify.feature.materializer.response.SerializedGraphQLResponseFactory
import funcify.feature.materializer.service.GraphQLSingleRequestMaterializationQueryExecutionStrategy
import funcify.feature.materializer.service.SingleRequestMaterializationExecutionResultPostprocessingService
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.GraphQLContext
import graphql.execution.ExecutionId
import graphql.execution.ExecutionIdProvider
import graphql.execution.instrumentation.Instrumentation
import kotlin.reflect.KClass
import org.slf4j.Logger
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2/19/22
 */
internal class DefaultGraphQLSingleRequestSessionCoordinator(
    private val materializationPreparsedDocumentProvider: MaterializationPreparsedDocumentProvider,
    private val instrumentation: Instrumentation,
    private val queryAsyncExecutionStrategy:
        GraphQLSingleRequestMaterializationQueryExecutionStrategy,
    private val singleRequestMaterializationExecutionResultPostprocessingService:
        SingleRequestMaterializationExecutionResultPostprocessingService,
    private val serializedGraphQLResponseFactory: SerializedGraphQLResponseFactory,
) : GraphQLSingleRequestSessionCoordinator {

    companion object {
        private val logger: Logger = loggerFor<DefaultGraphQLSingleRequestSessionCoordinator>()

        private object SessionExecutionIdProvider : ExecutionIdProvider {
            private val logger: Logger = loggerFor<SessionExecutionIdProvider>()

            override fun provide(
                query: String?,
                operationName: String?,
                context: Any?
            ): ExecutionId {
                return when (context) {
                    is GraphQLContext -> {
                        try {
                            context
                                .get<GraphQLSingleRequestSession>(
                                    GraphQLSingleRequestSession.GRAPHQL_SINGLE_REQUEST_SESSION_KEY
                                )
                                .rawGraphQLRequest
                                .executionId
                        } catch (t: Throwable) {
                            val message: String =
                                """context is missing entry for critical component [ key: %s ]"""
                                    .flatten()
                                    .format(
                                        GraphQLSingleRequestSession
                                            .GRAPHQL_SINGLE_REQUEST_SESSION_KEY
                                    )
                            logger.error("provide: [ status: failed ][ message: {} ]", message)
                            throw ServiceError.of(message)
                        }
                    }
                    else -> {
                        val message: String =
                            """unexpected context type: [ type: %s ] unable to retrieve execution_id"""
                                .flatten()
                                .format(
                                    context
                                        .toOption()
                                        .map { it::class }
                                        .mapNotNull(KClass<*>::qualifiedName)
                                        .getOrElse { "<NA>" }
                                )
                        logger.error("provide: [ status: failed ][ message: {} ]", message)
                        throw ServiceError.of(message)
                    }
                }
            }
        }
    }

    override fun conductSingleRequestSession(
        session: GraphQLSingleRequestSession
    ): Mono<GraphQLSingleRequestSession> {
        logger.info(
            """conduct_single_request_session: [ 
                |session.session_id: ${session.sessionId} 
                |]"""
                .flatten()
        )
        return Mono.fromCompletionStage(
                GraphQL.newGraphQL(session.materializationSchema)
                    .preparsedDocumentProvider(materializationPreparsedDocumentProvider)
                    .queryExecutionStrategy(queryAsyncExecutionStrategy)
                    .executionIdProvider(SessionExecutionIdProvider)
                    .instrumentation(instrumentation)
                    .build()
                    .executeAsync(executionInputBuilderUpdater(session))
            )
            .flatMap { executionResult: ExecutionResult ->
                when {
                    executionResult.extensions != null &&
                        executionResult.extensions.isNotEmpty() -> {
                        singleRequestMaterializationExecutionResultPostprocessingService
                            .postprocessExecutionResultWithExtensions(executionResult)
                    }
                    else -> {
                        Mono.fromSupplier {
                            session.update {
                                serializedGraphQLResponse(
                                    serializedGraphQLResponseFactory
                                        .builder()
                                        .executionResult(executionResult)
                                        .build()
                                )
                            }
                        }
                    }
                }
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
