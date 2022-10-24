package funcify.feature.materializer.session

import funcify.feature.materializer.request.GraphQLExecutionInputCustomizer
import funcify.feature.materializer.service.GraphQLSingleRequestMaterializationQueryExecutionStrategy
import funcify.feature.materializer.service.MaterializationPreparsedDocumentProvider
import funcify.feature.materializer.service.SingleRequestMaterializationExecutionResultPostprocessingService
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import org.slf4j.Logger
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2/19/22
 */
internal class DefaultGraphQLSingleRequestSessionCoordinator(
    private val materializationPreparsedDocumentProvider: MaterializationPreparsedDocumentProvider,
    private val queryExecutionStrategy: GraphQLSingleRequestMaterializationQueryExecutionStrategy,
    private val singleRequestMaterializationExecutionResultPostprocessingService:
        SingleRequestMaterializationExecutionResultPostprocessingService
) : GraphQLSingleRequestSessionCoordinator {

    companion object {
        private val logger: Logger = loggerFor<DefaultGraphQLSingleRequestSessionCoordinator>()
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
                GraphQL.newGraphQL(session.materializationSchema)
                    .preparsedDocumentProvider(materializationPreparsedDocumentProvider)
                    .queryExecutionStrategy(queryExecutionStrategy)
                    .build()
                    .executeAsync(executionInputBuilderUpdater(session))
            )
            .flatMap { executionResult: ExecutionResult ->
                singleRequestMaterializationExecutionResultPostprocessingService
                    .postprocessExecutionResult(executionResult)
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
