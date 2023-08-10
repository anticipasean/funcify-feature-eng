package funcify.feature.materializer.input

import arrow.core.Option
import arrow.core.getOrNone
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

/**
 * @author smccarron
 * @created 2023-07-30
 */
internal data class DefaultCommaSeparatedValuesInputContext(
    private val csvRecord: PersistentMap<String, String?>
) : RawInputContext.CommaSeparatedValues {

    private val fieldNameByIndex: ImmutableMap<Int, String> by lazy {
        csvRecord.keys.asSequence().withIndex().fold(persistentMapOf<Int, String>()) {
            pm: PersistentMap<Int, String>,
            (idx: Int, key: String) ->
            pm.put(idx, key)
        }
    }

    private val jsonForm: JsonNode by lazy {
        csvRecord.asSequence().fold(JsonNodeFactory.instance.objectNode()) {
            on: ObjectNode,
            (k: String, v: String?) ->
            on.put(k, v)
        }
    }

    override fun fieldNames(): ImmutableSet<String> {
        return csvRecord.keys
    }

    override fun get(fieldName: String): Option<String> {
        return csvRecord[fieldName].toOption()
    }

    override fun get(fieldIndex: Int): Option<String> {
        return fieldNameByIndex.getOrNone(fieldIndex).flatMap { k: String ->
            csvRecord[k].toOption()
        }
    }

    override fun asJsonNode(): JsonNode {
        return jsonForm
    }
}
