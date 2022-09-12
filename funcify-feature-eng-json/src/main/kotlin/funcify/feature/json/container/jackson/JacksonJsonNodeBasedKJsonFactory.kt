package funcify.feature.json.container.jackson

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.NumericNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import funcify.feature.json.container.KJsonContainerNode
import funcify.feature.json.container.KJsonScalarNode

internal object JacksonJsonNodeBasedKJsonFactory {

    private val nullNode: KJsonNullNode<Nothing> =
        KJsonNullNode<Nothing>(JsonNodeFactory.instance.nullNode())

    fun <WT> getNullNode(): KJsonNullNode<WT> {
        @Suppress("UNCHECKED_CAST") //
        return nullNode as KJsonNullNode<WT>
    }

    internal class KJsonObjectNode<WT>(val objectNode: ObjectNode) : KJsonContainerNode<WT, ObjectNode> {}

    internal class KJsonArrayNode<WT>(val arrayNode: ArrayNode) : KJsonContainerNode<WT, ArrayNode> {}

    internal class KJsonBooleanNode<WT>(val booleanNode: BooleanNode) : KJsonScalarNode<WT, BooleanNode> {}

    internal class KJsonNumericNode<WT>(val numericNode: NumericNode) : KJsonScalarNode<WT, NumericNode> {}

    internal class KJsonStringNode<WT>(val textNode: TextNode) : KJsonScalarNode<WT, TextNode> {}

    internal class KJsonNullNode<WT>(val nullNode: NullNode) : KJsonScalarNode<WT, NullNode> {}
}
