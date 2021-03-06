package funcify.feature.spring.service

import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.response.SerializedGraphQLResponse
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.materializer.session.GraphQLSingleRequestSessionFactory
import funcify.feature.spring.session.SpringGraphQLSingleRequestSessionCoordinator
import funcify.feature.tools.container.deferred.Deferred
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import org.slf4j.Logger
import org.springframework.stereotype.Component

/**
 *
 * @author smccarron
 * @created 2/20/22
 */
@Component
class SpringGraphQLSingleRequestExecutor(
    val graphQLSingleRequestSessionFactory: GraphQLSingleRequestSessionFactory,
    val springGraphQLSingleRequestSessionCoordinator: SpringGraphQLSingleRequestSessionCoordinator
) : GraphQLSingleRequestExecutor {

    companion object {
        private val logger: Logger = loggerFor<SpringGraphQLSingleRequestExecutor>()
    }

    override fun executeSingleRequest(
        rawGraphQLRequest: RawGraphQLRequest
    ): Deferred<SerializedGraphQLResponse> {
        logger.info(
            """execute_single_request: 
                |[ raw_graphql_request.request_id: 
                |${rawGraphQLRequest.requestId} ]
                |""".flattenIntoOneLine()
        )
        return graphQLSingleRequestSessionFactory
            .createSessionForSingleRequest(rawGraphQLRequest)
            .flatMap { session: GraphQLSingleRequestSession ->
                springGraphQLSingleRequestSessionCoordinator.conductSingleRequestSession(session)
            }
            .flatMap { session: GraphQLSingleRequestSession ->
                session.serializedGraphQLResponse.fold(
                    {
                        val message =
                            """session was not updated such that 
                              |a serialized_graphql_response was added to it
                              |""".flattenIntoOneLine()
                        Deferred.failed(IllegalStateException(message))
                    },
                    { sr -> Deferred.completed(sr) }
                )
            }
    }
}
