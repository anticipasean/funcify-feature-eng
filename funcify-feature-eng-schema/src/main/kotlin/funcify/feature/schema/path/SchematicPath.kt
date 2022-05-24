package funcify.feature.schema.path

import arrow.core.Option
import arrow.core.none
import arrow.core.some
import java.net.URI
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

/**
 * Represents a data element, derived or raw, within the schema, its arguments / parameters, and any
 * directives specifying additional contextual information or processing steps
 *
 * @author smccarron
 * @created 1/30/22
 */
interface SchematicPath {

    companion object {

        const val GRAPHQL_SCHEMATIC_PATH_SCHEME: String = "gqls"

        private val rootPath: SchematicPath = DefaultSchematicPath()

        fun getRootPath(): SchematicPath {
            return rootPath
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
    val arguments: ImmutableMap<String, String>

    /**
     * Represented by URI fragments `#uppercase&aliases=names=amount_remaining_3m_1m,amt_rem_3m1m`
     * in URI form and schema directives `@uppercase @aliases(names: ["amount_remaining_3m_1m",
     * "amt_rem_3m1m" ])` in GraphQL SDL form
     */
    val directives: ImmutableMap<String, String>

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

        fun argument(key: String, value: String): Builder

        fun argument(keyValuePair: Pair<String, String>): Builder

        fun arguments(keyValuePairs: Map<String, String>): Builder

        fun clearArguments(): Builder

        fun directive(key: String, value: String): Builder

        fun directive(keyValuePair: Pair<String, String>): Builder

        fun directive(keyValuePairs: Map<String, String>): Builder

        fun clearDirectives(): Builder

        fun build(): SchematicPath
    }
}
