package funcify.feature.schema.path

import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.none
import arrow.core.some
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import funcify.feature.json.JacksonJsonNodeComparator
import funcify.feature.tools.extensions.JsonNodeExtensions.removeLastChildKeyValuePairFromRightmostObjectNode
import java.math.BigDecimal
import java.net.URI
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toPersistentMap

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
                            .firstOrNull { keyOrValCompResult -> keyOrValCompResult != 0 }
                            ?: 0
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
                            .firstOrNull { compResult -> compResult != 0 }
                            ?: 0
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

    /**
     * Root doesn't have any path segments and doesn't have arguments or directives indicating it
     * represents a parameter to some source container or attribute type
     */
    fun isRoot(): Boolean {
        return pathSegments.isEmpty() && arguments.isEmpty() && directives.isEmpty()
    }

    /**
     * One schematic path is a parent to another when:
     * - parent has the same path segments _up to_ the child's final segment and neither parent nor
     * child schematic path represents parameters (=> have arguments or directives) to a source
     * container or attribute type e.g. `(Parent) gqls:/path_1 -> (Child) gqls:/path_1/path_2`
     * - parent has the same path segments as child does but child has arguments and/or directives
     * indicating it is a parameter specification for the parent source container or attribute type
     * e.g. `(Parent) gqls:/path_1 -> (Child) gqls:/path_1?argument_1=1`
     *
     * There is not a parent-child relationship when one path represents different parameters than
     * another _on the same_ source container or attribute type: `gqls:/path_1?argument_1=1 NOT
     * PARENT TO to gqls:/path_1#directive_1=23.3`
     */
    fun isParentTo(other: SchematicPath): Boolean {
        /**
         * Strategy: Attempt to make quick decisions based on size differences in path_segment lists
         * before checking for equality between path_segments
         */
        return when {
            this.scheme != other.scheme -> {
                false
            }
            /** Special handling for root or size of path_segments is zero */
            this.isRoot() -> {
                other.pathSegments.size == 0 &&
                    (other.arguments.isNotEmpty() || other.directives.isNotEmpty())
            }
            this.pathSegments.size > other.pathSegments.size -> {
                false
            }
            /**
             * All but last segment on other must match and both paths cannot represent parameter
             * indices (=> have arguments or directives)
             */
            this.pathSegments.size + 1 == other.pathSegments.size -> {
                pathSegments
                    .asSequence()
                    .zip(other.pathSegments.asSequence().take(this.pathSegments.size)) { t, o ->
                        t == o
                    }
                    .all { matched -> matched } &&
                    arguments.isEmpty() &&
                    directives.isEmpty() &&
                    other.arguments.isEmpty() &&
                    other.directives.isEmpty()
            }
            /**
             * All segments on both paths must match and other path must represent a parameter
             * junction index on this path
             */
            this.pathSegments.size == other.pathSegments.size &&
                this.arguments.isEmpty() &&
                this.directives.isEmpty() &&
                (other.arguments.isNotEmpty() || other.directives.isNotEmpty()) -> {
                this.pathSegments
                    .asSequence()
                    .zip(other.pathSegments.asSequence()) { t, o -> t == o }
                    .all { matched -> matched } &&
                    other.arguments.all { entry -> entry.value.isNull || entry.value.isEmpty } &&
                    other.directives.all { entry -> entry.value.isNull || entry.value.isEmpty }
            }
            /**
             * All segments on both paths must match and other path must represent a parameter
             * attribute index on this parameter junction index path
             */
            this.pathSegments.size == other.pathSegments.size &&
                (this.arguments.isNotEmpty() || this.directives.isNotEmpty()) &&
                (other.arguments.isNotEmpty() || other.directives.isNotEmpty()) -> {
                this.pathSegments
                    .asSequence()
                    .zip(other.pathSegments.asSequence()) { t, o -> t == o }
                    .all { matched -> matched } &&
                    other
                        .getParentPath()
                        .map { otherParentPath ->
                            stringKeyJsonValueMapComparator.compare(
                                this.arguments,
                                otherParentPath.arguments
                            ) == 0 &&
                                stringKeyJsonValueMapComparator.compare(
                                    this.directives,
                                    otherParentPath.directives
                                ) == 0
                        }
                        .getOrElse { false }
            }
            else -> {
                false
            }
        }
    }
    /**
     * Inverse of #isParentTo logic:
     *
     * One schematic path is a child to another when:
     * - child has N-1 path segments the same as the parent and neither parent nor child schematic
     * path represents parameters (=> have arguments or directives) to a source container or
     * attribute type e.g. `(Child) gqls:/path_1/path_2 -> (Parent) gqls:/path_1`
     * - child has the same path segments as parent does but child has arguments and/or directives
     * indicating it is a parameter specification for the parent source container or attribute type
     * e.g. `(Child) gqls:/path_1?argument_1=1 -> (Parent) gqls:/path_1`
     *
     * There is not a parent-child relationship when one path represents different parameters than
     * another _on the same_ source container or attribute type: `gqls:/path_1?argument_1=1 NOT
     * CHILD TO to gqls:/path_1#directive_1=23.3`
     */
    fun isChildTo(other: SchematicPath): Boolean {
        /**
         * Strategy: Attempt to make quick decisions based on size differences in path_segment lists
         * before checking for equality between path_segments
         */
        return when {
            this.scheme != other.scheme -> {
                false
            }
            /** Special case for root or number of path_segments equal to zero */
            other.isRoot() -> {
                this.pathSegments.size == 0 &&
                    (this.arguments.isNotEmpty() || this.directives.isNotEmpty())
            }
            this.pathSegments.size < other.pathSegments.size -> {
                false
            }
            /**
             * All path_segments _up to last one_ must match and neither path can represent a
             * parameter index (=> have arguments or directives)
             */
            this.pathSegments.size == other.pathSegments.size + 1 &&
                arguments.isEmpty() &&
                directives.isEmpty() &&
                other.arguments.isEmpty() &&
                other.directives.isEmpty() -> {
                this.pathSegments
                    .asSequence()
                    .take(other.pathSegments.size)
                    .zip(other.pathSegments.asSequence()) { t, o -> t == o }
                    .all { matched -> matched }
            }
            /**
             * All path_segments must match and this path must represent a parameter junction index
             * on the other path (=> have arguments or directives)
             */
            this.pathSegments.size == other.pathSegments.size &&
                (this.arguments.isNotEmpty() || this.directives.isNotEmpty()) &&
                other.arguments.isEmpty() &&
                other.directives.isEmpty() -> {
                this.pathSegments
                    .asSequence()
                    .zip(other.pathSegments.asSequence()) { t, o -> t == o }
                    .all { matched -> matched } &&
                    this.arguments.all { entry -> entry.value.isNull || entry.value.isEmpty } &&
                    this.directives.all { entry -> entry.value.isNull || entry.value.isEmpty }
            }
            /**
             * All segments on both paths must match and this path must represent a parameter
             * attribute index on the other parameter junction index
             */
            this.pathSegments.size == other.pathSegments.size &&
                (this.arguments.isNotEmpty() || this.directives.isNotEmpty()) &&
                (other.arguments.isNotEmpty() || other.directives.isNotEmpty()) -> {
                this.pathSegments
                    .asSequence()
                    .zip(other.pathSegments.asSequence()) { t, o -> t == o }
                    .all { matched -> matched } &&
                    this.getParentPath()
                        .map { thisParentPath ->
                            stringKeyJsonValueMapComparator.compare(
                                thisParentPath.arguments,
                                other.arguments
                            ) == 0 &&
                                stringKeyJsonValueMapComparator.compare(
                                    thisParentPath.directives,
                                    other.directives
                                ) == 0
                        }
                        .getOrElse { false }
            }
            else -> {
                false
            }
        }
    }

    /**
     * Siblings must represent the same parent source container or attribute type within the schema
     * (=> share the same N - 1 path segments)
     *
     * e.g. `gqls:/path_1/path_2/path_3 IS SIBLING TO gqls:/path_1/path_2/path_4`
     *
     * OR
     *
     * represent parameters on the same parent source container or attribute type within the schema
     * (=> share the same N path segments) but represent different parameters on that source
     * container or attribute type:
     *
     * e.g. `gqls:/path_1/path_2?argument_1=1 IS SIBLING TO gqls:/path_1/path_2#directive_1=1`
     */
    fun isSiblingTo(other: SchematicPath): Boolean {
        /**
         * Strategy: Attempt to make quick decisions based on size differences in path_segment lists
         * before checking for equality between path_segments
         */
        return when {
            this.scheme != other.scheme -> {
                false
            }
            else -> {
                this.getParentPath()
                    .zip(other.getParentPath(), schematicPathComparator::compare)
                    .map { comp -> comp == 0 }
                    .getOrElse { false }
            }
        }
    }

    /**
     * Has all path segments in common up to other.path_segments.size - 1 index and with all path
     * segments being equal, this path is a parameter index (child) to the other
     */
    fun isDescendentOf(other: SchematicPath): Boolean {
        /**
         * Strategy: Attempt to make quick decisions based on size differences in path_segment lists
         * before checking for equality between path_segments
         */
        return when {
            this.scheme != other.scheme -> {
                false
            }
            /** Every path but root itself is a descendent of root */
            other.isRoot() -> {
                !this.isRoot()
            }
            /** if other has more path segments, not a descendent but could be an ancestor */
            this.pathSegments.size < other.pathSegments.size -> {
                false
            }
            /**
             * if other has fewer path segments than this one, then this is a descendent of the
             * other if all path segments of the other match those within this path
             */
            this.pathSegments.size > other.pathSegments.size && other.pathSegments.size > 0 -> {
                this.pathSegments
                    .asSequence()
                    .take(other.pathSegments.size)
                    .zip(other.pathSegments.asSequence()) { t, o -> t == o }
                    .all { matched -> matched }
            }
            /**
             * if this path represents a parameter index--has arguments and/or directives--on the
             * other and the other represents a source index
             */
            this.pathSegments.size == other.pathSegments.size &&
                (this.arguments.isNotEmpty() || this.directives.isNotEmpty()) &&
                other.arguments.isEmpty() &&
                other.directives.isEmpty() -> {
                this.pathSegments
                    .asSequence()
                    .zip(other.pathSegments.asSequence()) { t, o -> t == o }
                    .all { matched -> matched }
            }
            /** both represent parameter indices */
            // TODO: Revisit this ancestry logic and handle recursive comparison cases in a more
            // nuanced manner
            // Use with caution in the meantime
            this.pathSegments.size == other.pathSegments.size &&
                (this.arguments.isNotEmpty() || this.directives.isNotEmpty()) &&
                (other.arguments.isNotEmpty() || other.directives.isNotEmpty()) -> {
                this.pathSegments
                    .asSequence()
                    .zip(other.pathSegments.asSequence()) { t, o -> t == o }
                    .all { matched -> matched } &&
                    stringKeyJsonValueMapComparator.compare(this.arguments, other.arguments).let {
                        argComp ->
                        if (argComp == 0) {
                            stringKeyJsonValueMapComparator.compare(
                                this.directives,
                                other.directives
                            )
                        } else {
                            argComp
                        }
                    } > 0
            }
            else -> {
                false
            }
        }
    }

    /**
     * Has all path segments in common up to this.path_segments.size - 1 index and with all path
     * segments being equal, this path is not a parameter index (child) for the other
     */
    fun isAncestorOf(other: SchematicPath): Boolean {
        /**
         * Strategy: Attempt to make quick decisions based on size differences in path_segment lists
         * before checking for equality between path_segments
         */
        return when {
            this.scheme != other.scheme -> {
                false
            }
            /** root is an ancestor to all but itself */
            this.isRoot() -> {
                !other.isRoot()
            }
            /** if other has fewer path segments, not an ancestor but could be descendent */
            this.pathSegments.size > other.pathSegments.size -> {
                false
            }
            this.pathSegments.size < other.pathSegments.size && this.pathSegments.size > 0 -> {
                this.pathSegments
                    .asSequence()
                    .zip(other.pathSegments.asSequence().take(this.pathSegments.size)) { t, o ->
                        t == o
                    }
                    .all { matched -> matched }
            }
            this.pathSegments.size == other.pathSegments.size &&
                arguments.isEmpty() &&
                directives.isEmpty() &&
                (other.arguments.isNotEmpty() || other.directives.isNotEmpty()) -> {
                this.pathSegments
                    .asSequence()
                    .zip(other.pathSegments.asSequence()) { t, o -> t == o }
                    .all { matched -> matched }
            }
            // TODO: Revisit this ancestry logic and handle recursive comparison cases in a more
            // nuanced manner
            this.pathSegments.size == other.pathSegments.size &&
                (arguments.isNotEmpty() || directives.isNotEmpty()) &&
                (other.arguments.isNotEmpty() || other.directives.isNotEmpty()) -> {
                this.pathSegments
                    .asSequence()
                    .zip(other.pathSegments.asSequence()) { t, o -> t == o }
                    .all { matched -> matched } &&
                    stringKeyJsonValueMapComparator.compare(this.arguments, other.arguments).let {
                        argComp ->
                        if (argComp == 0) {
                            stringKeyJsonValueMapComparator.compare(
                                this.directives,
                                other.directives
                            )
                        } else {
                            argComp
                        }
                    } < 0
            }
            else -> {
                false
            }
        }
    }

    fun level(): Int = pathSegments.size

    /**
     * A parent path is:
     * - one that represents the container if the current path represents an attribute on that
     * container
     *
     * _OR_
     * - one that represents an attribute on a data source to which the value represented by the
     * current path may be passed as a parameter
     */
    fun getParentPath(): Option<SchematicPath> {
        return when {
            isRoot() -> {
                none<SchematicPath>()
            }
            arguments.isEmpty() && directives.isEmpty() -> {
                transform { dropPathSegment() }.some()
            }
            arguments.isNotEmpty() &&
                directives.isEmpty() &&
                arguments.size == 1 &&
                arguments.firstNotNullOf { (_, value) -> value.isNull || value.isEmpty } -> {
                transform { clearArguments() }.some()
            }
            arguments.isEmpty() &&
                directives.isNotEmpty() &&
                directives.size == 1 &&
                directives.firstNotNullOf { (_, value) -> value.isNull || value.isEmpty } -> {
                transform { clearDirectives() }.some()
            }
            else -> {
                /**
                 * Remove the last directive if it represents a container: null or empty json node
                 * Else, make the last directive json value null node
                 */
                if (directives.isNotEmpty()) {
                    val lastKey = directives.keys.last()
                    directives[lastKey]?.let { jn ->
                        // represents a scalar value, array, or null so remove entry with last_key
                        if (jn.isEmpty || !jn.isObject) {
                            transform {
                                    clearDirectives()
                                        .directives(directives.toPersistentMap().remove(lastKey))
                                }
                                .some()
                        } else {
                            // represents an object so remove rightmost child keyvalue pair,
                            // potentially nested
                            transform {
                                    directive(
                                        lastKey,
                                        jn.removeLastChildKeyValuePairFromRightmostObjectNode()
                                    )
                                }
                                .some()
                        }
                    }
                        ?: none<SchematicPath>()
                    /**
                     * Remove the last argument if it represents a container: null or empty Else,
                     * make the last argument json value null node
                     */
                } else if (arguments.isNotEmpty()) {
                    val lastKey = arguments.keys.last()
                    arguments[lastKey]?.let { jn ->
                        // represents a scalar value, array, or null so remove entry with last_key
                        if (jn.isEmpty || !jn.isObject) {
                            transform {
                                    clearArguments()
                                        .arguments(arguments.toPersistentMap().remove(lastKey))
                                }
                                .some()
                        } else {
                            // represents an object so remove rightmost child keyvalue pair,
                            // potentially nested
                            transform {
                                    argument(
                                        lastKey,
                                        jn.removeLastChildKeyValuePairFromRightmostObjectNode()
                                    )
                                }
                                .some()
                        }
                    }
                        ?: none<SchematicPath>()
                } else {
                    none<SchematicPath>()
                }
            }
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
