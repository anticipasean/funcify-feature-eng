package funcify.feature.schema.path

import arrow.core.Option
import arrow.core.none
import arrow.core.some
import java.net.URI
import kotlinx.collections.immutable.ImmutableList

/**
 * Represents a data element, derived or raw, within the schema, its arguments / parameters, and any
 * directives specifying additional contextual information or processing steps
 *
 * @author smccarron
 * @created 1/30/22
 */
interface SchematicPath : Comparable<SchematicPath> {

    companion object {

        const val GRAPHQL_SCHEMATIC_PATH_SCHEME: String = "mlfs"

        private val rootPath: SchematicPath = DefaultSchematicPath()

        @JvmStatic
        fun getRootPath(): SchematicPath {
            return rootPath
        }

        @JvmStatic
        fun of(builderFunction: Builder.() -> Builder): SchematicPath {
            return rootPath.transform(builderFunction)
        }

        @JvmStatic
        fun comparator(): Comparator<SchematicPath> {
            return SchematicPathComparator
        }

        /** @param input in the form of a URI string */
        @JvmStatic
        fun parseOrThrow(input: String): SchematicPath {
            return SchematicPathParser(input).orElseThrow()
        }

        /** @param input in the form of a URI string */
        @JvmStatic
        fun parseOrNull(input: String): SchematicPath? {
            return SchematicPathParser(input).orNull()
        }

        /** @param input in the form of a URI */
        @JvmStatic
        fun fromURIOrThrow(input: URI): SchematicPath {
            return SchematicPathParser.fromURI(input).orElseThrow()
        }
    }

    val scheme: String
    /**
     * Represented by URI path segments `/user/transactions/messageBody` in URI form and a
     * query/mutation/subscription graph structure:
     * ```
     * user(id: 123) {
     *     transactions(
     *       filter: {
     *         correlation_id: { eq: "82b1d1cd-8020-41f1-9536-dc143c320ff1" } @alias(name: "traceId")
     *       }
     *     ) {
     *         messageBody
     *     }
     * }
     * ```
     *
     * in GraphQL query form where the referent is the `messageBody` field =>
     * `/user/transactions/messageBody`
     */
    val pathSegments: ImmutableList<String>

    /**
     * Represented by URI query parameters `?filter=/correlation_id/eq` in URI form and a
     * query/mutation/subscription graph structure:
     * ```
     * user(id: 123) {
     *     transactions(
     *       filter: {
     *         correlation_id: { eq: "82b1d1cd-8020-41f1-9536-dc143c320ff1" } @alias(name: "traceId")
     *       }
     *     ) {
     *         messageBody
     *     }
     * }
     * ```
     *
     * in GraphQL query form where the referent is `eq`, the key to the value being passed as an
     * input object to `correlation_id`, an input object to `filter`, an argument to field
     * `transactions` => `/user/transactions?filter=/correlation_id/eq`
     */
    val argument: Option<Pair<String, ImmutableList<String>>>

    /**
     * Represented by URI fragments `#alias` in URI form and a query/mutation/subscription graph
     * structure:
     * ```
     * user(id: 123) {
     *     transactions(
     *       filter: {
     *         correlation_id: { eq: "82b1d1cd-8020-41f1-9536-dc143c320ff1" } @alias(name: "traceId")
     *       }
     *     ) {
     *         messageBody
     *     }
     * }
     * ```
     *
     * in GraphQL query form where the referent is `alias`, a directive on `correlation_id`, an
     * input object to `filter`, an argument to field `transactions` =>
     * `/user/transactions?filter=/correlation_id#alias`
     */
    val directive: Option<Pair<String, ImmutableList<String>>>

    /** URI representation of path on which feature function is located within service context */
    fun toURI(): URI

    /**
     * Root doesn't have any path segments and doesn't have arguments or directives indicating it
     * represents a parameter to some source container or attribute type
     */
    fun isRoot(): Boolean {
        return pathSegments.isEmpty() && argument.isEmpty() && directive.isEmpty()
    }

    fun level(): Int = pathSegments.size

    fun getParentPath(): Option<SchematicPath> {
        return when {
            pathSegments.isEmpty() && argument.isEmpty() && directive.isEmpty() -> {
                none<SchematicPath>()
            }
            argument.isEmpty() && directive.isEmpty() -> {
                transform { dropPathSegment() }.some()
            }
            argument
                .filter { (_: String, pathSegments: ImmutableList<String>) ->
                    pathSegments.isEmpty()
                }
                .isDefined() && directive.isEmpty() -> {
                transform { clearArgument() }.some()
            }
            argument.isEmpty() &&
                directive
                    .filter { (_: String, pathSegments: ImmutableList<String>) ->
                        pathSegments.isEmpty()
                    }
                    .isDefined() -> {
                transform { clearDirective() }.some()
            }
            argument
                .filter { (_: String, pathSegments: ImmutableList<String>) ->
                    pathSegments.isNotEmpty()
                }
                .isDefined() && directive.isEmpty() -> {
                transform { dropArgumentPathSegment() }.some()
            }
            argument.isDefined() &&
                directive
                    .filter { (_: String, pathSegments: ImmutableList<String>) ->
                        pathSegments.isEmpty()
                    }
                    .isDefined() -> {
                transform { clearDirective() }.some()
            }
            else -> {
                transform { dropDirectivePathSegment() }.some()
            }
        }
    }

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

        fun argument(name: String, pathSegments: List<String>): Builder

        fun argument(name: String, vararg pathSegment: String): Builder

        fun prependArgumentPathSegment(vararg pathSegment: String): Builder

        fun prependArgumentPathSegments(pathSegments: List<String>): Builder

        fun appendArgumentPathSegment(vararg pathSegment: String): Builder

        fun appendArgumentPathSegments(pathSegments: List<String>): Builder

        fun dropArgumentPathSegment(): Builder

        fun clearArgument(): Builder

        fun directive(name: String, pathSegments: List<String>): Builder

        fun directive(name: String, vararg pathSegment: String): Builder

        fun prependDirectivePathSegment(vararg pathSegment: String): Builder

        fun prependDirectivePathSegments(pathSegments: List<String>): Builder

        fun appendDirectivePathSegment(vararg pathSegment: String): Builder

        fun appendDirectivePathSegments(pathSegments: List<String>): Builder

        fun dropDirectivePathSegment(): Builder

        fun clearDirective(): Builder

        fun build(): SchematicPath
    }
}
