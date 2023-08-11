package funcify.feature.materializer.dispatch

import funcify.feature.error.ServiceError
import funcify.feature.materializer.graph.RequestMaterializationGraph
import funcify.feature.materializer.session.GraphQLSingleRequestSession
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import java.time.Duration
import org.slf4j.Logger
import reactor.core.publisher.Mono

internal class DefaultSingleRequestMaterializationDispatchService(
// private val dispatchedRequestMaterializationGraphFactory:
//    DispatchedRequestMaterializationGraphFactory
) : SingleRequestMaterializationDispatchService {

    companion object {
        private val logger: Logger = loggerFor<DefaultSingleRequestMaterializationDispatchService>()
        private const val DEFAULT_EXTERNAL_CALL_TIMEOUT_SECONDS: Int = 4
        private val DEFAULT_EXTERNAL_CALL_TIMEOUT_DURATION: Duration =
            Duration.ofSeconds(DEFAULT_EXTERNAL_CALL_TIMEOUT_SECONDS.toLong())
    }

    override fun dispatchRequestsInMaterializationGraphInSession(
        session: GraphQLSingleRequestSession
    ): Mono<GraphQLSingleRequestSession> {
        logger.info(
            "dispatch_requests_in_materialization_graph_in_session: [ session.session_id: ${session.sessionId} ]"
        )
        return when (
            val requestMaterializationGraph: RequestMaterializationGraph? =
                session.requestMaterializationGraph.orNull()
        ) {
            null -> {
                Mono.error<GraphQLSingleRequestSession> {
                    ServiceError.of(
                        "request_materialization_graph has not been defined in session [ session.session_id: {} ]",
                        session.sessionId
                    )
                }
            }
            else -> {
                dispatchRequestsForCallablesInMaterializationGraph(
                    session,
                    requestMaterializationGraph
                )
            }
        }
    }

    private fun dispatchRequestsForCallablesInMaterializationGraph(
        session: GraphQLSingleRequestSession,
        requestMaterializationGraph: RequestMaterializationGraph,
    ): Mono<GraphQLSingleRequestSession> {
        TODO("Not yet implemented")
    }
}
