package funcify.feature.json

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.*

object JacksonJsonNodeComparator : Comparator<JsonNode> {

    private val nodeTypeComparator: Comparator<JsonNodeType> by lazy {
        val nodeTypeOrderMap: Map<JsonNodeType, Int> by lazy {
            JsonNodeType.values()
                .asSequence()
                .map { jnt ->
                    // `when` expression ensures if any enum type is added
                    // the following will not compile without the
                    // addition of the remaining branch
                    jnt to
                        when (jnt) {
                            JsonNodeType.NULL, JsonNodeType.MISSING -> 0
                            JsonNodeType.BOOLEAN -> 1
                            JsonNodeType.NUMBER -> 2
                            JsonNodeType.STRING, JsonNodeType.BINARY -> 3
                            JsonNodeType.ARRAY -> 4
                            JsonNodeType.OBJECT -> 5
                            JsonNodeType.POJO -> 6
                        }
                }
                .toMap()
        }
        Comparator { jnt1, jnt2 -> nodeTypeOrderMap[jnt1]!! - nodeTypeOrderMap[jnt2]!! }
    }

    private val booleanNodeScalarComparator: Comparator<BooleanNode> by lazy {
        Comparator { o1, o2 -> o1.booleanValue().compareTo(o2.booleanValue()) }
    }
    private val numericNodeComparator: Comparator<NumericNode> by lazy {
        Comparator { o1, o2 ->
            when (o1.numberType()) {
                null ->
                    if (o2.numberType() == null) {
                        0
                    } else {
                        -1
                    }
                JsonParser.NumberType.INT,
                JsonParser.NumberType.LONG,
                JsonParser.NumberType.BIG_INTEGER -> {
                    when (o2.numberType()) {
                        null -> 1
                        JsonParser.NumberType.INT,
                        JsonParser.NumberType.LONG,
                        JsonParser.NumberType.BIG_INTEGER -> {
                            o1.bigIntegerValue().compareTo(o2.bigIntegerValue())
                        }
                        JsonParser.NumberType.FLOAT,
                        JsonParser.NumberType.DOUBLE,
                        JsonParser.NumberType.BIG_DECIMAL -> {
                            o1.decimalValue().compareTo(o2.decimalValue())
                        }
                    }
                }
                JsonParser.NumberType.FLOAT,
                JsonParser.NumberType.DOUBLE,
                JsonParser.NumberType.BIG_DECIMAL -> {
                    o1.decimalValue().compareTo(o2.decimalValue())
                }
            }
        }
    }
    private val textNodeScalarComparator: Comparator<TextNode> by lazy {
        Comparator { o1, o2 -> o1.textValue().compareTo(o2.textValue()) }
    }

    private val binaryNodeScalarComparator: Comparator<BinaryNode> by lazy {
        Comparator { o1, o2 ->
            // The smaller bytearray will go first if the byte arrays match up to the smaller one's
            // n-1 node
            val sizeComparisonBias: Int =
                when (val sizeComparison = o1.binaryValue().size.compareTo(o2.binaryValue().size)) {
                    0 -> 0
                    else -> sizeComparison
                }
            o1.binaryValue()
                .asSequence()
                .zip(o2.binaryValue().asSequence()) { b1, b2 -> b1.compareTo(b2) }
                .firstOrNull { comp -> comp != 0 }
                ?: sizeComparisonBias
        }
    }

    override fun compare(o1: JsonNode?, o2: JsonNode?): Int {
        if (o1 == null || o2 == null) {
            return when {
                o1 == null && o2 == null -> 0
                o1 == null -> -1
                else -> 1
            }
        }
        return compareNonNullNodes(o1, o2)
    }

    private fun compareNonNullNodes(o1: JsonNode, o2: JsonNode) =
        when (val nodeTypeComparison = nodeTypeComparator.compare(o1.nodeType, o2.nodeType)) {
            0 -> {
                compareSimilarNodeTypes(o1, o2)
            }
            else -> {
                nodeTypeComparison
            }
        }

    private fun compareSimilarNodeTypes(o1: JsonNode, o2: JsonNode): Int {
        when {
            (o1 is NullNode || o1 is MissingNode) && (o2 is NullNode || o2 is MissingNode) -> {
                return 0
            }
            o1 is BooleanNode && o2 is BooleanNode -> {
                return compareBooleanNodes(o1, o2)
            }
            o1 is NumericNode && o2 is NumericNode -> {
                return compareNumericNodes(o1, o2)
            }
            o1 is TextNode && o2 is TextNode -> {
                return compareTextNodes(o1, o2)
            }
            o1 is BinaryNode && o2 is BinaryNode -> {
                return compareBinaryNodes(o1, o2)
            }
            o1 is ArrayNode && o2 is ArrayNode -> {
                return compareArrayNodes(o1, o2)
            }
            o1 is ObjectNode && o2 is ObjectNode -> {
                return compareObjectNodes(o1, o2)
            }
            o1 is POJONode && o2 is POJONode -> {
                return comparePojoNodes(o1, o2)
            }
            else -> {
                return o1.toString().compareTo(o2.toString())
            }
        }
    }

    private fun compareBooleanNodes(o1: BooleanNode, o2: BooleanNode): Int {
        return booleanNodeScalarComparator.compare(o1, o2)
    }

    private fun compareNumericNodes(o1: NumericNode, o2: NumericNode): Int {
        return numericNodeComparator.compare(o1, o2)
    }

    private fun compareTextNodes(o1: TextNode, o2: TextNode): Int {
        return textNodeScalarComparator.compare(o1, o2)
    }

    private fun compareBinaryNodes(o1: BinaryNode, o2: BinaryNode): Int {
        return binaryNodeScalarComparator.compare(o1, o2)
    }

    private fun compareArrayNodes(o1: ArrayNode, o2: ArrayNode): Int {
        // The smaller array_node will go first if the two array_nodes match up to the smaller
        // node's n-1 json_node
        val sizeComparisonBias: Int =
            when (val sizeComparison = o1.size().compareTo(o2.size())) {
                0 -> 0
                else -> sizeComparison
            }
        return o1.asSequence()
            .zip(o2.asSequence()) { n1, n2 ->
                // recursive call
                compareNonNullNodes(n1, n2)
            }
            .firstOrNull { comp -> comp != 0 }
            ?: sizeComparisonBias
    }

    private fun compareObjectNodes(o1: ObjectNode, o2: ObjectNode): Int {
        // The smaller object_node will go first if the two object_node's keys and values match up
        // to the smaller one's n-1 entry
        val sizeComparisonBias: Int =
            when (val sizeComparison = o1.size().compareTo(o2.size())) {
                0 -> 0
                else -> sizeComparison
            }
        return o1.fields()
            .asSequence()
            .zip(o2.fields().asSequence()) { (k1, v1), (k2, v2) ->
                if (k1 == null || k2 == null) {
                    when {
                        k1 == null && k2 == null -> 0
                        k1 == null -> -1
                        else -> 1
                    }
                } else {
                    when (val keyComparison = k1.compareTo(k2)) {
                        0 -> {
                            // recursive call
                            compareNonNullNodes(v1, v2)
                        }
                        else -> {
                            keyComparison
                        }
                    }
                }
            }
            .firstOrNull { comp -> comp != 0 }
            ?: sizeComparisonBias
    }

    private fun comparePojoNodes(o1: POJONode, o2: POJONode): Int {
        // Not much that can be done without any clear type information or use cases
        return when {
            o1.pojo == o2.pojo -> 0
            else -> o1.toString().compareTo(o2.toString())
        }
    }
}
