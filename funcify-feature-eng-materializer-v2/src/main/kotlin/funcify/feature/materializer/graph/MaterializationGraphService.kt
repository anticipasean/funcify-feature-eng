package funcify.feature.materializer.graph

import funcify.feature.materializer.session.MaterializationSession
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2/9/22
 */
interface MaterializationGraphService<M : MaterializationSession> {

    fun createRequestMaterializationGraphForSession(session: M): Mono<M>
}
