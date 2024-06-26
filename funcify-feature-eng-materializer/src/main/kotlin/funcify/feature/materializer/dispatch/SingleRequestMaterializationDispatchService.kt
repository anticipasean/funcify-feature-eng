package funcify.feature.materializer.dispatch

import funcify.feature.materializer.session.request.GraphQLSingleRequestSession
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2022-08-24
 */
interface SingleRequestMaterializationDispatchService :
    MaterializationRequestDispatchService<GraphQLSingleRequestSession> {

    override fun dispatchRequestsInMaterializationGraphInSession(
        session: GraphQLSingleRequestSession
    ): Mono<out GraphQLSingleRequestSession>
}
