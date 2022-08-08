package funcify.feature.spring.service

import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.response.SerializedGraphQLResponse
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.materializer.session.GraphQLSingleRequestSessionCoordinator
import funcify.feature.materializer.session.GraphQLSingleRequestSessionFactory
import funcify.feature.spring.error.FeatureEngSpringWebFluxException
import funcify.feature.spring.error.SpringWebFluxErrorResponse
import funcify.feature.tools.container.deferred.Deferred
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import org.slf4j.Logger
import org.springframework.stereotype.Component

/**
 *
 * @author smccarron
 * @created 2/20/22
 */
@Component
internal class SpringGraphQLSingleRequestExecutor(
    private val graphQLSingleRequestSessionFactory: GraphQLSingleRequestSessionFactory,
    private val graphQLSingleRequestSessionCoordinator: GraphQLSingleRequestSessionCoordinator
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
                |""".flatten()
        )
        return graphQLSingleRequestSessionFactory
            .createSessionForSingleRequest(rawGraphQLRequest)
            .flatMap { session: GraphQLSingleRequestSession ->
                graphQLSingleRequestSessionCoordinator.conductSingleRequestSession(session)
            }
            .flatMap { session: GraphQLSingleRequestSession ->
                session.serializedGraphQLResponse.fold(
                    {
                        val message =
                            """session was not updated such that 
                              |a serialized_graphql_response was added to it
                              |""".flatten()
                        Deferred.failed(
                            FeatureEngSpringWebFluxException(
                                SpringWebFluxErrorResponse.NO_RESPONSE_PROVIDED,
                                message
                            )
                        )
                    },
                    { sr -> Deferred.completed(sr) }
                )
            }
    }
}
