package funcify.feature.spring.service

import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.response.SerializedGraphQLResponse
import funcify.feature.materializer.service.GraphQLRequestExecutor
import funcify.feature.materializer.session.GraphQLExecutionSessionFactory
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
open class SpringGraphQLRequestExecutor(
    val graphQLExecutionSessionFactory: GraphQLExecutionSessionFactory,
    val sessionCoordinator: GraphQLWebFluxSessionCoordinator
) : GraphQLRequestExecutor {

    companion object {
        private val logger: Logger = loggerFor<SpringGraphQLRequestExecutor>()
    }

    override fun executeSingleRequest(
        rawGraphQLRequest: RawGraphQLRequest
    ): Deferred<SerializedGraphQLResponse> {
        logger.info(
            """execute_single_request: 
                |[ raw_graphql_request: ${rawGraphQLRequest.requestId} ]
                |""".flattenIntoOneLine()
        )
        return Deferred.completed(
                graphQLExecutionSessionFactory.createSessionForSingleRequest(rawGraphQLRequest)
            )
            .flatMap { session -> sessionCoordinator.conductSingleRequestSession(session) }
            .flatMap { session ->
                session.serializedGraphQLResponse.fold(
                    {
                        val message =
                            """
                               |session was not updated such that 
                               |a serialized_graphql_response was added to it
                               """.flattenIntoOneLine()
                        Deferred.failed(IllegalStateException(message))
                    },
                    { sr -> Deferred.completed(sr) }
                )
            }
    }
}
