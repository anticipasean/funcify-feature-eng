package funcify.feature.materializer.input

import funcify.feature.materializer.session.MaterializationSession
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-07-31
 */
interface MaterializationRawInputContextExtractor<M : MaterializationSession> {

    fun extractRawInputContextIfProvided(session: M): Mono<out M>
}
