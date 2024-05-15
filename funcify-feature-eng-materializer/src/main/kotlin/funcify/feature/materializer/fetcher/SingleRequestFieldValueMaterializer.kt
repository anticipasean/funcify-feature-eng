package funcify.feature.materializer.fetcher

import funcify.feature.materializer.session.field.SingleRequestFieldMaterializationSession
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2022-08-29
 */
interface SingleRequestFieldValueMaterializer :
    FieldValueMaterializer<SingleRequestFieldMaterializationSession> {

    override fun materializeValueForFieldInSession(
        session: SingleRequestFieldMaterializationSession
    ): Mono<Any?>
}
