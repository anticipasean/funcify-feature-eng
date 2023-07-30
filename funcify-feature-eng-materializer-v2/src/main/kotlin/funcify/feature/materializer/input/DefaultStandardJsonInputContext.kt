package funcify.feature.materializer.input

import com.fasterxml.jackson.databind.JsonNode

/**
 * @author smccarron
 * @created 2023-07-30
 */
internal data class DefaultStandardJsonInputContext(private val jsonNode: JsonNode) :
    RawInputContext.StandardJson {

    override fun asJsonNode(): JsonNode {
        return jsonNode
    }
}
