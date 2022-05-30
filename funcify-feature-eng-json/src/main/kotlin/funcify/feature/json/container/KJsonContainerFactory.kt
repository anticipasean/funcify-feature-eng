package funcify.feature.json.container

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.NumericNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode

internal object KJsonContainerFactory {

    internal interface KJsonNode<WT, I>

    internal class KJsonObjectNode<WT>(val objectNode: ObjectNode) : KJsonNode<WT, ObjectNode> {}

    internal class KJsonArrayNode<WT>(val arrayNode: ArrayNode) : KJsonNode<WT, ArrayNode> {}

    internal class KJsonBooleanNode<WT>(val booleanNode: BooleanNode) :
        KJsonNode<WT, BooleanNode> {}

    internal class KJsonNumericNode<WT>(val numericNode: NumericNode) :
        KJsonNode<WT, NumericNode> {}

    internal class KJsonStringNode<WT>(val textNode: TextNode) : KJsonNode<WT, TextNode> {}

    internal class KJsonNullNode<WT>(val nullNode: NullNode) : KJsonNode<WT, NullNode> {}
}
