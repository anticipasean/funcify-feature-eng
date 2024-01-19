package funcify.feature.materializer.fetcher

import funcify.feature.materializer.session.MaterializationSession
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2/9/22
 */
interface FieldValueMaterializer<M : MaterializationSession> {

    fun materializeValueForFieldInSession(session: M): Mono<Any?>
}
