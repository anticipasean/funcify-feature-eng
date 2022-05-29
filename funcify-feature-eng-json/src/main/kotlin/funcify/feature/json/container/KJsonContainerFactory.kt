package funcify.feature.json.container

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.NumericNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode

internal object KJsonContainerFactory {

    internal interface KJsonContainer<WT, I>

    internal class KJsonObjectContainer<WT>(val objectNode: ObjectNode) :
        KJsonContainer<WT, ObjectNode> {}

    internal class KJsonArrayContainer<WT>(val arrayNode: ArrayNode) :
        KJsonContainer<WT, ArrayNode> {}

    internal class KJsonBooleanContainer<WT>(val booleanNode: BooleanNode) :
        KJsonContainer<WT, BooleanNode> {}

    internal class KJsonNumericContainer<WT>(val numericNode: NumericNode) :
        KJsonContainer<WT, NumericNode> {}

    internal class KJsonStringContainer<WT>(val textNode: TextNode) :
        KJsonContainer<WT, TextNode> {}

    internal class KJsonNullContainer<WT>(val nullNode: NullNode) : KJsonContainer<WT, NullNode> {}
}
