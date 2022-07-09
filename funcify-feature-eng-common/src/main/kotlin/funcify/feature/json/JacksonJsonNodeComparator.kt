package funcify.feature.json

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.*

object JacksonJsonNodeComparator : Comparator<JsonNode> {

    /**
     * Biased [JsonNodeType] comparator:
     * - null or missing values are treated as equal and go before scalars
     * - scalars(*) (i.e. Booleans, Numerics, and Strings) before container types (i.e. Arrays,
     * Objects)
     * - [BinaryNode]s (=> [ByteArray]s ) before [ArrayNode]s (=> [JsonNode] arrays)
     * - arrays before objects(^)
     *
     * (*) The reasoning behind this: **Scalar** here would be any value of a type not requiring
     * _explicit_ iteration over its parts to assess equality or deduce a value for ordering
     * instances. [String]s _technically can_ be iterated over when treated as character arrays but
     * [String]'s implementation of the [Comparable] contract at least comes standard in Java and
     * Kotlin, whereas those of ByteArray, ArrayNode, and ObjectNode do not
     *
     * (^) The reasoning behind this: Arrays are treated in these comparisons as an iterable of
     * pairs with each pair's first value representing the index value e.g. `[ (0, "cake"), (1,
     * "frosting"), ... ]` and objects are treated in comparison as an iterable of pairs with each
     * pair's first value representing the [String] key used to get the [JsonNode] value. Array
     * indices in a such an iterable `[0..n-1]` would then lexicographically come before object keys
     * `["cake", "frosting"]` when compared side-by-side.
     * ```
     *  >>> [str(0),str(1)] > ["cake", "frosting"]
     *  False
     *  >>> [str(0),str(1)] < ["cake", "frosting"]
     *  True
     * ```
     */
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
                            JsonNodeType.STRING -> 3
                            JsonNodeType.BINARY -> 4
                            JsonNodeType.ARRAY -> 5
                            JsonNodeType.OBJECT -> 6
                            JsonNodeType.POJO -> 7
                        }
                }
                .toMap()
        }
        Comparator { jnt1, jnt2 -> nodeTypeOrderMap[jnt1]!! - nodeTypeOrderMap[jnt2]!! }
    }

    private val booleanNodeScalarComparator: Comparator<BooleanNode> by lazy {
        Comparator { o1, o2 -> o1.booleanValue().compareTo(o2.booleanValue()) }
    }

    /**
     * Biased numeric node comparator:
     * - if their numeric type information is missing/null, then apply _null first_ comparison logic
     * - if both numeric nodes represent _integral_ numbers, compare them as [java.math.BigInteger]s
     * - if either node represents a floating point number, compare them as [java.math.BigDecimal]s
     * in order to avoid any loss of information a conversion to a binary floating point number
     * might incur and leverage [java.math.BigDecimal]'s Comparable contract
     * - if either node represents a NaN in a floating point number type, treat NaN as missing/null
     * values would be treated
     */
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
                            if (o2.isNaN) {
                                1
                            } else {
                                o1.decimalValue().compareTo(o2.decimalValue())
                            }
                        }
                    }
                }
                JsonParser.NumberType.FLOAT,
                JsonParser.NumberType.DOUBLE,
                JsonParser.NumberType.BIG_DECIMAL -> {
                    if (o1.isNaN || o2.isNaN) {
                        when {
                            o1.isNaN && o2.isNaN -> 0
                            o1.isNaN -> -1
                            else -> 1
                        }
                    } else {
                        o1.decimalValue().compareTo(o2.decimalValue())
                    }
                }
            }
        }
    }
    private val textNodeScalarComparator: Comparator<TextNode> by lazy {
        Comparator { o1, o2 -> o1.textValue().compareTo(o2.textValue()) }
    }

    private val binaryNodeScalarComparator: Comparator<BinaryNode> by lazy {
        Comparator { o1, o2 ->
            // The smaller byte array will go first if the byte arrays match up to the smaller one's
            // n-1 node
            val sizeComparisonBias: Int = o1.binaryValue().size.compareTo(o2.binaryValue().size)
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

    private fun compareNonNullNodes(o1: JsonNode, o2: JsonNode): Int {
        return when (val nodeTypeComparison = nodeTypeComparator.compare(o1.nodeType, o2.nodeType)
        ) {
            0 -> {
                compareSimilarNodeTypes(o1, o2)
            }
            else -> {
                nodeTypeComparison
            }
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

    /**
     * Biases:
     * - Assume order of entries is significant, so no sorting is performed before comparison
     * - [JsonNode] _elements_ cannot be null and are replaced with [NullNode]s by Jackson code when
     * performing updates on a given [ArrayNode] (If Jackson's API fails to do this for any case,
     * comparison could result in a [NullPointerException])
     */
    private fun compareArrayNodes(o1: ArrayNode, o2: ArrayNode): Int {
        // The smaller array_node will go first if the two array_nodes match up to the smaller
        // node's n-1 json_node
        val sizeComparisonBias: Int = o1.size().compareTo(o2.size())
        return o1.asSequence()
            .zip(o2.asSequence()) { n1, n2 ->
                // potentially recursive call if both nodes are array_nodes
                compareNonNullNodes(n1, n2)
            }
            .firstOrNull { comp -> comp != 0 }
            ?: sizeComparisonBias
    }

    /**
     * Biases:
     * - Assume order of entries is significant and do not sort entries by [String] keys before
     * comparison. Jackson [ObjectNode]s are backed by [LinkedHashMap]s that preserve insertion
     * order so the order of the entries is not _random_ or based on hash code values of the keys
     * (as it would be if the backing [Map] container type were [HashMap])
     * - [String] _keys_ can be "null" and null keys come first
     * - [JsonNode] _values_ cannot be null and are replaced with [NullNode]s by Jackson code when
     * performing updates on a given [ObjectNode] (If Jackson's API fails to do this for any case,
     * comparison could result in a [NullPointerException])
     * - _keys_ are compared before _values_ by entry-to-entry and **not** set-to-set
     */
    private fun compareObjectNodes(o1: ObjectNode, o2: ObjectNode): Int {
        // The smaller object_node will go first if the two object_node's keys and values match up
        // to the smaller one's n-1 entry
        val sizeComparisonBias: Int = o1.size().compareTo(o2.size())
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
                            // potentially recursive call if both nodes are object_nodes
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
        return when (o1) {
            o2 -> 0
            else ->
                // Not much else that can be done without any clear type information or use case
                // If Jackson is unable to serialize these nodes into JSON strings, then throw an
                // error indicating that
                try {
                    o1.toString().compareTo(o2.toString())
                } catch (t: Throwable) {
                    throw IllegalStateException(
                        "unable to compare these two ${POJONode::class.qualifiedName} instances without error",
                        t
                    )
                }
        }
    }
}
