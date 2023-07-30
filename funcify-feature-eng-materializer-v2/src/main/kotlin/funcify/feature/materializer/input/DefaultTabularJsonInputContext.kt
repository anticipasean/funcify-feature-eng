package funcify.feature.materializer.input

import arrow.core.Option
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toPersistentSet

/**
 * @author smccarron
 * @created 2023-07-30
 */
internal data class DefaultTabularJsonInputContext(private val jsonNode: JsonNode) :
    RawInputContext.TabularJson {

    private val fieldNamesSet: ImmutableSet<String> by lazy {
        jsonNode.fieldNames().asSequence().toPersistentSet()
    }

    override fun fieldNames(): Set<String> {
        return fieldNamesSet
    }

    override fun get(fieldName: String): Option<JsonNode> {
        return jsonNode.get(fieldName).toOption()
    }

    override fun asJsonNode(): JsonNode {
        return jsonNode
    }
}
