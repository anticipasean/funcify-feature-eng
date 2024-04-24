package funcify.feature.materializer.output

import arrow.core.Option
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.materializer.session.field.SingleRequestFieldMaterializationSession

/**
 * @author smccarron
 * @created 2024-04-23
 */
interface SingleRequestJsonFieldValueDeserializer :
    JsonFieldValueDeserializer<SingleRequestFieldMaterializationSession> {

    override fun deserializeValueForFieldFromJsonInSession(
        session: SingleRequestFieldMaterializationSession,
        jsonValue: JsonNode
    ): Option<Any>
}
