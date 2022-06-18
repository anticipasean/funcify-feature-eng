package funcify.feature.schema.path

import arrow.core.Option
import arrow.core.none
import arrow.core.some
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import java.math.BigDecimal
import java.net.URI
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import net.thisptr.jackson.jq.internal.misc.JsonNodeComparator

/**
 * Represents a data element, derived or raw, within the schema, its arguments / parameters, and any
 * directives specifying additional contextual information or processing steps
 *
 * @author smccarron
 * @created 1/30/22
 */
interface SchematicPath : Comparable<SchematicPath> {

    companion object {

        const val GRAPHQL_SCHEMATIC_PATH_SCHEME: String = "gqls"

        private val rootPath: SchematicPath = DefaultSchematicPath()

        fun getRootPath(): SchematicPath {
            return rootPath
        }

        private val comparator: Comparator<SchematicPath> by lazy {
            val jsonNodeComparator: JsonNodeComparator = JsonNodeComparator.getInstance()
            val strJsonMapComparatorFunction:
                (Map<String, JsonNode>, Map<String, JsonNode>) -> Int =
                { map1, map2 -> //
                    /*
                     * Early exit approach: First non-matching pair found returns comparison value else considered equal
                     */
                    when (val mapSizeComparison: Int = map1.size.compareTo(map2.size)) {
                        0 -> {
                            map1.asSequence()
                                .zip(map2.asSequence())
                                .firstOrNull { (e1, e2) ->
                                    e1.key.compareTo(e2.key) != 0 ||
                                        jsonNodeComparator.compare(e1.value, e2.value) != 0
                                }
                                ?.let { (e1, e2) ->
                                    when (val keyComparison: Int = e1.key.compareTo(e2.key)) {
                                        0 -> jsonNodeComparator.compare(e1.value, e2.value)
                                        else -> keyComparison
                                    }
                                }
                                ?: 0
                        }
                        else -> {
                            mapSizeComparison
                        }
                    }
                }
            val strListComparatorFunction: (List<String>, List<String>) -> Int = { l1, l2 -> //
                /*
                 * Early exit approach: First non-matching pair found returns comparison value else considered equal
                 */
                when (val listSizeComparison: Int = l1.size.compareTo(l2.size)) {
                    0 -> {
                        l1.asSequence()
                            .zip(l2.asSequence())
                            .firstOrNull { (s1, s2) -> s1.compareTo(s2) != 0 }
                            ?.let { (s1, s2) -> s1.compareTo(s2) }
                            ?: 0
                    }
                    else -> listSizeComparison
                }
            }
            Comparator.comparing({ sp: SchematicPath -> sp.scheme }, String::compareTo)
                .thenComparing({ sp: SchematicPath -> sp.pathSegments }, strListComparatorFunction)
                .thenComparing({ sp: SchematicPath -> sp.arguments }, strJsonMapComparatorFunction)
                .thenComparing({ sp: SchematicPath -> sp.directives }, strJsonMapComparatorFunction)
        }

        fun comparator(): Comparator<SchematicPath> {
            return comparator
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
     * ``` in GraphQL query form
     * ```
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
     * in URI form and schema directives `@uppercase @aliases(names: ["amount_remaining_3m_1m",
     * "amt_rem_3m1m" ])` in GraphQL SDL form
     */
    val directives: ImmutableMap<String, JsonNode>

    /** URI representation of path on which feature function is located within service context */
    fun toURI(): URI

    fun isRoot(): Boolean {
        return pathSegments.isEmpty()
    }

    fun isParentTo(other: SchematicPath): Boolean {
        return when {
            this.scheme != other.scheme -> {
                false
            }
            this.pathSegments.size >= other.pathSegments.size -> {
                false
            }
            this.pathSegments.size + 1 == other.pathSegments.size -> {
                pathSegments
                    .asSequence()
                    .zip(other.pathSegments.asSequence()) { a: String, b: String -> a == b }
                    .all { matched -> matched }
            }
            else -> {
                false
            }
        }
    }
    fun isChildTo(other: SchematicPath): Boolean {
        return when {
            this.scheme != other.scheme -> {
                false
            }
            this.pathSegments.size <= other.pathSegments.size -> {
                false
            }
            this.pathSegments.size - 1 == other.pathSegments.size -> {
                other
                    .pathSegments
                    .asSequence()
                    .zip(this.pathSegments.asSequence()) { a: String, b: String -> a == b }
                    .all { matched -> matched }
            }
            else -> {
                false
            }
        }
    }

    fun isSiblingTo(other: SchematicPath): Boolean {
        return when {
            this.scheme != other.scheme -> {
                false
            }
            /** Is root a sibling to itself? Yes??? */
            this.pathSegments.size == 0 && this.pathSegments.size == 0 -> {
                true
            }
            /** not same number of levels, then not siblings */
            this.pathSegments.size != other.pathSegments.size -> {
                false
            }
            /** siblings in the context of root */
            this.pathSegments.size == 1 && other.pathSegments.size == 1 -> {
                true
            }
            this.pathSegments.size == other.pathSegments.size -> {
                /** Assumes path_segments.size must be greater than 1 if here */
                val parentPathSegmentsSize = pathSegments.size - 1
                pathSegments
                    .asSequence()
                    .take(parentPathSegmentsSize)
                    .zip(other.pathSegments.asSequence().take(parentPathSegmentsSize)) {
                        a: String,
                        b: String ->
                        a == b
                    }
                    .all { matched -> matched }
            }
            else -> {
                false
            }
        }
    }

    /**
     * Has at least one path segment in common starting from root, with other having fewer path
     * segments
     */
    fun isDescendentOf(other: SchematicPath): Boolean {
        return when {
            this.scheme != other.scheme -> {
                false
            }
            /** if root or same level, not descendents */
            pathSegments.size == other.pathSegments.size -> {
                false
            }
            /** if other has more path segments, not a descendent but could be an ancestor */
            pathSegments.size < other.pathSegments.size -> {
                false
            }
            /**
             * if other has fewer path segments and the first segment of each matches, then this
             * path is descendent
             */
            pathSegments.size > other.pathSegments.size && other.pathSegments.size > 0 -> {
                pathSegments[0] == other.pathSegments[0]
            }
            else -> {
                false
            }
        }
    }

    /**
     * Has at least one path segment in common starting from root, with other having more path
     * segments
     */
    fun isAncestorOf(other: SchematicPath): Boolean {
        return when {
            this.scheme != other.scheme -> {
                false
            }
            /** if root or same level, not ancestors */
            pathSegments.size == other.pathSegments.size -> {
                false
            }
            /** if other has fewer path segments, not an ancestor but could be descendent */
            pathSegments.size > other.pathSegments.size -> {
                false
            }
            /**
             * if other has more path segments and the first segment of each matches, then this path
             * is ancestor
             */
            pathSegments.size < other.pathSegments.size && pathSegments.size > 0 -> {
                pathSegments[0] == other.pathSegments[0]
            }
            else -> {
                false
            }
        }
    }

    fun level(): Int = pathSegments.size

    fun getParentPath(): Option<SchematicPath> {
        return when {
            isRoot() -> {
                none<SchematicPath>()
            }
            else -> transform { dropPathSegment() }.some()
        }
    }

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

        fun directive(keyValuePairs: Map<String, JsonNode>): Builder

        fun clearDirectives(): Builder

        fun build(): SchematicPath
    }
}
