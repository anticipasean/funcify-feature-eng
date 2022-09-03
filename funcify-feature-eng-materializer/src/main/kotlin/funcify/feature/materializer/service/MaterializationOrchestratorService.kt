package funcify.feature.materializer.service

import funcify.feature.materializer.session.MaterializationSession
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2/9/22
 */
interface MaterializationOrchestratorService<M : MaterializationSession> {

    fun materializeValueInSession(session: M): Mono<Any>
}
