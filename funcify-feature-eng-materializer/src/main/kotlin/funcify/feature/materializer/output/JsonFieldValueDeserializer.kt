package funcify.feature.materializer.output

import arrow.core.Option
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.materializer.session.MaterializationSession

/**
 * @author smccarron
 * @created 2024-04-23
 */
interface JsonFieldValueDeserializer<M : MaterializationSession> {

    fun deserializeValueForFieldFromJsonInSession(session: M, jsonValue: JsonNode): Option<Any>

}
