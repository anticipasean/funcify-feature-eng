package funcify.feature.materializer.coordinator

import funcify.feature.materializer.session.request.GraphQLSingleRequestSession
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2/19/22
 */
interface GraphQLSingleRequestSessionCoordinator {

    fun conductSingleRequestSession(
        session: GraphQLSingleRequestSession
    ): Mono<GraphQLSingleRequestSession>
}
