package funcify.feature.materializer.service

import funcify.feature.materializer.session.GraphQLSingleRequestSession
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2022-08-08
 */
interface SingleRequestMaterializationGraphService :
    MaterializationGraphService<GraphQLSingleRequestSession> {

    override fun createRequestMaterializationGraphForSession(
        session: GraphQLSingleRequestSession
    ): Mono<GraphQLSingleRequestSession>
}
