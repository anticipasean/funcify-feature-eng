package funcify.feature.spring.session

import funcify.feature.materializer.request.GraphQLExecutionInputCustomizer
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.materializer.session.GraphQLSingleRequestSessionCoordinator
import funcify.feature.tools.container.async.KFuture
import funcify.feature.tools.container.deferred.Deferred
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import java.util.concurrent.Executor
import org.slf4j.Logger
import org.springframework.stereotype.Component

/**
 *
 * @author smccarron
 * @created 2/19/22
 */
@Component
class SpringGraphQLSingleRequestSessionCoordinator(private val asyncExecutor: Executor) :
    GraphQLSingleRequestSessionCoordinator {

    companion object {
        private val logger: Logger = loggerFor<SpringGraphQLSingleRequestSessionCoordinator>()
    }

    override fun conductSingleRequestSession(
        session: GraphQLSingleRequestSession
    ): Deferred<GraphQLSingleRequestSession> {
        logger.info(
            """conduct_single_request_session: [ 
                |session.session_id: ${session.sessionId} ]
                |""".flattenIntoOneLine()
        )
        return Deferred.fromKFuture(
                KFuture.of(
                    completionStage =
                        GraphQL.newGraphQL(session.materializationSchema)
                            .build()
                            .executeAsync(executionInputBuilderUpdater(session)),
                    executor = asyncExecutor
                )
            )
            .map { er: ExecutionResult ->
                // TODO: Replace with logic for handling execution_result and updating
                // session
                session
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
            ) { bldr: ExecutionInput.Builder, customizer: GraphQLExecutionInputCustomizer ->
                customizer.invoke(bldr)
            }
        }
    }
}
