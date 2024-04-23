package funcify.feature.schema.path.lookup

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import funcify.feature.tools.json.JacksonJsonNodeComparator
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import java.math.BigDecimal
import java.net.URI

/**
 * Represents the location of a value within registered feature definitions, the folder structure
 * and any arguments on which the given value depended for its calculation
 *
 * @author smccarron
 * @created 1/30/22
 */
interface SchematicPath : Comparable<SchematicPath> {

    companion object {

        const val SCHEMATIC_PATH_SCHEME: String = "mlfs"

        private val rootPath: SchematicPath = DefaultSchematicPath()

        @JvmStatic
        fun getRootPath(): SchematicPath {
            return rootPath
        }

        @JvmStatic
        fun of(builderFunction: Builder.() -> Builder): SchematicPath {
            return rootPath.transform(builderFunction)
        }

        private val stringKeyJsonValueMapComparator: Comparator<Map<String, JsonNode>> by lazy {
            val jsonNodeComparator: Comparator<JsonNode> = JacksonJsonNodeComparator
            Comparator<Map<String, JsonNode>> { map1, map2 -> //
                /*
                 * Early exit approach: First non-matching pair found returns comparison value else considered equal
                 */
                when (val mapSizeComparison: Int = map1.size.compareTo(map2.size)) {
                    0 -> {
                        map1
                            .asSequence()
                            .zip(map2.asSequence())
                            .map { (e1, e2) ->
                                e1.key.compareTo(e2.key).let { keyComp ->
                                    if (keyComp != 0) {
                                        keyComp
                                    } else {
                                        jsonNodeComparator.compare(e1.value, e2.value)
                                    }
                                }
                            }
                            .firstOrNull { keyOrValCompResult -> keyOrValCompResult != 0 } ?: 0
                    }
                    else -> {
                        mapSizeComparison
                    }
                }
            }
        }

        private val stringListComparator: Comparator<List<String>> by lazy {
            Comparator<List<String>> { l1, l2 -> //
                /*
                 * Early exit approach: First non-matching pair found returns comparison value else considered equal
                 */
                when (val listSizeComparison: Int = l1.size.compareTo(l2.size)) {
                    0 -> {
                        l1.asSequence()
                            .zip(l2.asSequence())
                            .map { (s1, s2) -> s1.compareTo(s2) }
                            .firstOrNull { compResult -> compResult != 0 } ?: 0
                    }
                    else -> listSizeComparison
                }
            }
        }

        private val schematicPathComparator: Comparator<SchematicPath> by lazy {
            Comparator.comparing(SchematicPath::scheme, String::compareTo)
                .thenComparing(SchematicPath::pathSegments, stringListComparator)
                .thenComparing(SchematicPath::arguments, stringKeyJsonValueMapComparator)
                .thenComparing(SchematicPath::directives, stringKeyJsonValueMapComparator)
        }

        @JvmStatic
        fun comparator(): Comparator<SchematicPath> {
            return schematicPathComparator
        }
    }

    val scheme: String
    /**
     * Represented by URI path segments `/user/transactions/messageBody` in URI form and a
     * query/mutation/subscription graph structure:
     * ```
     * user(id: 123) {
     *     transactions(filter: { correlation_id: { eq: "82b1d1cd-8020-41f1-9536-dc143c320ff1" } }) {
     *         messageBody
     *     }
     * }
     * ```
     *
     * in GraphQL query form
     */
    val pathSegments: ImmutableList<String>

    /**
     * Represented by URI query parameters
     * `?correlation_id=82b1d1cd-8020-41f1-9536-dc143c320ff1&user_id=123` in URI form and contextual
     * input arguments `node(context: {"correlation_id":
     * "82b1d1cd-8020-41f1-9536-dc143c320ff1","user_id": 123})` in GraphQL SDL form
     */
    val arguments: ImmutableMap<String, JsonNode>

    /**
     * Represented by URI fragments `#uppercase&aliases=names=amount_remaining_3m_1m,amt_rem_3m1m`
     * in URI form and schema directives
     * `@uppercase @aliases(names: ["amount_remaining_3m_1m", "amt_rem_3m1m" ])` in GraphQL SDL form
     */
    val directives: ImmutableMap<String, JsonNode>

    /** URI representation of path on which feature function is located within service context */
    fun toURI(): URI

    /**
     * Root doesn't have any path segments and doesn't have arguments or directives indicating it
     * represents a parameter to some source container or attribute type
     */
    fun isRoot(): Boolean {
        return pathSegments.isEmpty() && arguments.isEmpty() && directives.isEmpty()
    }

    fun level(): Int = pathSegments.size

    fun toDecodedURIString(): String

    override fun compareTo(other: SchematicPath): Int {
        return comparator().compare(this, other)
    }

    fun transform(mapper: Builder.() -> Builder): SchematicPath

    interface Builder {

        fun scheme(scheme: String): Builder

        fun prependPathSegment(vararg pathSegment: String): Builder

        fun prependPathSegments(pathSegments: List<String>): Builder

        fun appendPathSegment(vararg pathSegment: String): Builder {
            return pathSegment(*pathSegment)
        }

        fun appendPathSegments(pathSegments: List<String>): Builder {
            return pathSegments(pathSegments)
        }

        fun dropPathSegment(): Builder

        fun pathSegment(vararg pathSegment: String): Builder

        fun pathSegments(pathSegments: List<String>): Builder

        fun clearPathSegments(): Builder

        fun argument(key: String, value: JsonNode): Builder

        fun argument(key: String, stringValue: String): Builder {
            return argument(key, JsonNodeFactory.instance.textNode(stringValue))
        }

        fun argument(key: String, intValue: Int): Builder {
            return argument(key, JsonNodeFactory.instance.numberNode(intValue))
        }

        fun argument(key: String, longValue: Long): Builder {
            return argument(key, JsonNodeFactory.instance.numberNode(longValue))
        }

        fun argument(key: String, doubleValue: Double): Builder {
            return argument(key, JsonNodeFactory.instance.numberNode(doubleValue))
        }

        fun argument(key: String, floatValue: Float): Builder {
            return argument(key, JsonNodeFactory.instance.numberNode(floatValue))
        }

        fun argument(key: String, bigDecimalValue: BigDecimal): Builder {
            return argument(key, JsonNodeFactory.instance.numberNode(bigDecimalValue))
        }

        fun argument(key: String, booleanValue: Boolean): Builder {
            return argument(key, JsonNodeFactory.instance.booleanNode(booleanValue))
        }

        fun argument(key: String): Builder {
            return argument(key, JsonNodeFactory.instance.nullNode())
        }

        fun argument(keyValuePair: Pair<String, JsonNode>): Builder

        fun arguments(keyValuePairs: Map<String, JsonNode>): Builder

        fun dropArgument(key: String): Builder

        fun clearArguments(): Builder

        fun directive(key: String, value: JsonNode): Builder

        fun directive(key: String, stringValue: String): Builder {
            return directive(key, JsonNodeFactory.instance.textNode(stringValue))
        }

        fun directive(key: String, intValue: Int): Builder {
            return directive(key, JsonNodeFactory.instance.numberNode(intValue))
        }

        fun directive(key: String, longValue: Long): Builder {
            return directive(key, JsonNodeFactory.instance.numberNode(longValue))
        }

        fun directive(key: String, doubleValue: Double): Builder {
            return directive(key, JsonNodeFactory.instance.numberNode(doubleValue))
        }

        fun directive(key: String, floatValue: Float): Builder {
            return directive(key, JsonNodeFactory.instance.numberNode(floatValue))
        }

        fun directive(key: String, bigDecimalValue: BigDecimal): Builder {
            return directive(key, JsonNodeFactory.instance.numberNode(bigDecimalValue))
        }

        fun directive(key: String, booleanValue: Boolean): Builder {
            return directive(key, JsonNodeFactory.instance.booleanNode(booleanValue))
        }

        fun directive(key: String): Builder {
            return directive(key, JsonNodeFactory.instance.nullNode())
        }

        fun directive(keyValuePair: Pair<String, JsonNode>): Builder

        fun directives(keyValuePairs: Map<String, JsonNode>): Builder

        fun dropDirective(key: String): Builder

        fun clearDirectives(): Builder

        fun build(): SchematicPath
    }
}
