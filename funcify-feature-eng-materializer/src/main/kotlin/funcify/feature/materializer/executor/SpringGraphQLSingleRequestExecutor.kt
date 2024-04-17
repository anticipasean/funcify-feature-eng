package funcify.feature.materializer.executor

import funcify.feature.error.ServiceError
import funcify.feature.materializer.coordinator.GraphQLSingleRequestSessionCoordinator
import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.response.SerializedGraphQLResponse
import funcify.feature.materializer.session.factory.GraphQLSingleRequestSessionFactory
import funcify.feature.materializer.session.request.GraphQLSingleRequestSession
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import org.slf4j.Logger
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2/20/22
 */
internal class SpringGraphQLSingleRequestExecutor(
    private val graphQLSingleRequestSessionFactory: GraphQLSingleRequestSessionFactory,
    private val graphQLSingleRequestSessionCoordinator: GraphQLSingleRequestSessionCoordinator
) : GraphQLSingleRequestExecutor {

    companion object {
        private val logger: Logger = loggerFor<SpringGraphQLSingleRequestExecutor>()
    }

    override fun executeSingleRequest(
        rawGraphQLRequest: RawGraphQLRequest
    ): Mono<out SerializedGraphQLResponse> {
        logger.info(
            """execute_single_request: 
                |[ raw_graphql_request.request_id: 
                |${rawGraphQLRequest.requestId} ]
                |"""
                .flatten()
        )
        return graphQLSingleRequestSessionFactory
            .createSessionForSingleRequest(rawGraphQLRequest)
            .flatMap { s: GraphQLSingleRequestSession ->
                graphQLSingleRequestSessionCoordinator.conductSingleRequestSession(s)
            }
            .flatMap { s: GraphQLSingleRequestSession ->
                when (val sgr: SerializedGraphQLResponse? = s.serializedGraphQLResponse.orNull()) {
                    null -> {
                        Mono.error {
                            ServiceError.of(
                                "session does not contain %s",
                                SerializedGraphQLResponse::class.simpleName
                            )
                        }
                    }
                    else -> {
                        Mono.just(sgr)
                    }
                }
            }
    }
}
