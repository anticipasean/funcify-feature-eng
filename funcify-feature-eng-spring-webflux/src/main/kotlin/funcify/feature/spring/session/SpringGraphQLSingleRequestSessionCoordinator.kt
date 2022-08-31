package funcify.feature.spring.session

import funcify.feature.materializer.request.GraphQLExecutionInputCustomizer
import funcify.feature.materializer.response.SerializedGraphQLResponse
import funcify.feature.materializer.response.SerializedGraphQLResponseFactory
import funcify.feature.materializer.service.MaterializationPreparsedDocumentProvider
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.materializer.session.GraphQLSingleRequestSessionCoordinator
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
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
                    .build()
                    .executeAsync(executionInputBuilderUpdater(session)),
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
