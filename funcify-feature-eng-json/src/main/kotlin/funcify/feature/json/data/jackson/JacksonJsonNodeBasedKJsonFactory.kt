package funcify.feature.json.data.jackson

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.NumericNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import funcify.feature.json.data.KJsonContainerData
import funcify.feature.json.data.KJsonScalarData

internal object JacksonJsonNodeBasedKJsonFactory {

    private val nullNode: KJsonNullData<Nothing> =
        KJsonNullData<Nothing>(JsonNodeFactory.instance.nullNode())

    fun <WT> getNullNode(): KJsonNullData<WT> {
        @Suppress("UNCHECKED_CAST") //
        return nullNode as KJsonNullData<WT>
    }

    internal class KJsonObjectData<WT>(val objectNode: ObjectNode) : KJsonContainerData<WT, ObjectNode> {}

    internal class KJsonArrayData<WT>(val arrayNode: ArrayNode) : KJsonContainerData<WT, ArrayNode> {}

    internal class KJsonBooleanData<WT>(val booleanNode: BooleanNode) : KJsonScalarData<WT, BooleanNode> {}

    internal class KJsonNumericData<WT>(val numericNode: NumericNode) : KJsonScalarData<WT, NumericNode> {}

    internal class KJsonStringData<WT>(val textNode: TextNode) : KJsonScalarData<WT, TextNode> {}

    internal class KJsonNullData<WT>(val nullNode: NullNode) : KJsonScalarData<WT, NullNode> {}
}
