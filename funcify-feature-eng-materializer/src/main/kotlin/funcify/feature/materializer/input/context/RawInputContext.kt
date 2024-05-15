package funcify.feature.materializer.input.context

import arrow.core.Option
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.collections.immutable.ImmutableSet

/**
 * @author smccarron
 * @created 2023-07-29
 */
interface RawInputContext {

    companion object {
        const val RAW_INPUT_CONTEXT_VARIABLE_KEY: String = "input"
        const val RAW_INPUT_CONTEXT_VARIABLE_PREFIX: String = "_rawInput"
    }

    fun fieldNames(): ImmutableSet<String>

    fun get(fieldName: String): Option<JsonNode>

    // TODO: Consider whether a defensive deepCopy needs to be provided, especially if this type is
    // used outside of materializer module
    fun asJsonNode(): JsonNode

    interface Builder {

        fun json(jsonNode: JsonNode): Builder

        fun mapRecord(mapRecord: Map<*, *>): Builder

        fun build(): RawInputContext
    }
}
