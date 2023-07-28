package funcify.feature.materializer.service

import funcify.feature.materializer.session.GraphQLSingleRequestSession
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2022-08-24
 */
interface SingleRequestMaterializationDispatchService :
    MaterializationRequestDispatchService<GraphQLSingleRequestSession> {

    override fun dispatchRequestsInMaterializationGraphInSession(
        session: GraphQLSingleRequestSession
    ): Mono<GraphQLSingleRequestSession>
}
