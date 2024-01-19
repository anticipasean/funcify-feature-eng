package funcify.feature.materializer.graph

import funcify.feature.materializer.session.request.GraphQLSingleRequestSession
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2022-08-08
 */
interface SingleRequestMaterializationGraphService :
    MaterializationGraphService<GraphQLSingleRequestSession> {

    override fun createRequestMaterializationGraphForSession(
        session: GraphQLSingleRequestSession
    ): Mono<out GraphQLSingleRequestSession>
}
