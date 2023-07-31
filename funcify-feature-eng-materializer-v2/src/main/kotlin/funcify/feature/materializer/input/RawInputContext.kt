package funcify.feature.materializer.input

import arrow.core.Option
import com.fasterxml.jackson.databind.JsonNode

/**
 * @author smccarron
 * @created 2023-07-29
 */
sealed interface RawInputContext {

    companion object {
        val RAW_INPUT_CONTEXT_VARIABLE_KEY: String = "input"
    }

    // TODO: Consider whether a defensive deepCopy needs to be provided, especially if this type is
    // used outside of materializer module
    fun asJsonNode(): JsonNode

    interface CommaSeparatedValues : RawInputContext {

        fun fieldNames(): Set<String>

        fun get(fieldName: String): Option<String>

        fun get(fieldIndex: Int): Option<String>

        override fun asJsonNode(): JsonNode
    }

    interface TabularJson : RawInputContext {

        fun fieldNames(): Set<String>

        fun get(fieldName: String): Option<JsonNode>

        override fun asJsonNode(): JsonNode
    }

    interface StandardJson : RawInputContext {

        override fun asJsonNode(): JsonNode
    }

    interface Builder {

        fun json(jsonNode: JsonNode): Builder

        fun csvRecord(csvRecord: Map<String, String?>): Builder

        fun build(): RawInputContext
    }
}
