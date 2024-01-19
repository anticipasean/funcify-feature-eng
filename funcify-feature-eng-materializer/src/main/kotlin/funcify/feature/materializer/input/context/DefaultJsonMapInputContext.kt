package funcify.feature.materializer.input.context

import arrow.core.Option
import arrow.core.getOrNone
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap

/**
 * @author smccarron
 * @created 2023-08-19
 */
internal data class DefaultJsonMapInputContext(
    private val jsonByFieldName: PersistentMap<String, JsonNode>
) : RawInputContext {

    private val jsonForm: JsonNode by lazy {
        jsonByFieldName.asSequence().fold(JsonNodeFactory.instance.objectNode()) {
            on: ObjectNode,
            (k: String, v: JsonNode) ->
            on.set(k, v)
        }
    }

    override fun fieldNames(): ImmutableSet<String> {
        return jsonByFieldName.keys
    }

    override fun get(fieldName: String): Option<JsonNode> {
        return jsonByFieldName.getOrNone(fieldName)
    }

    override fun asJsonNode(): JsonNode {
        return jsonForm
    }
}
