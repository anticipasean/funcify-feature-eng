package funcify.feature.schema.path.operation

import arrow.core.Option
import arrow.core.none
import arrow.core.some
import java.net.URI
import kotlinx.collections.immutable.ImmutableList

/**
 * Refers a specific component of / location within a GraphQL query: field, field on an inline
 * fragment, field on a fragment spread, argument on a field, input value on an argument, directive
 * on a field or argument, directive on an argument value, etc.
 *
 * @author smccarron
 * @created 1/30/22
 */
interface GQLOperationPath : Comparable<GQLOperationPath> {

    companion object {

        const val GRAPHQL_OPERATION_PATH_SCHEME: String = "gqlo"

        private val rootPath: GQLOperationPath = DefaultGQLOperationPath()

        @JvmStatic
        fun getRootPath(): GQLOperationPath {
            return rootPath
        }

        @JvmStatic
        fun of(builderFunction: Builder.() -> Builder): GQLOperationPath {
            return rootPath.transform(builderFunction)
        }

        @JvmStatic
        fun comparator(): Comparator<GQLOperationPath> {
            return GQLOperationPathComparator
        }

        /** @param input in the form of a URI string */
        @JvmStatic
        fun parseOrThrow(input: String): GQLOperationPath {
            return GQLOperationPathParser(input).orElseThrow()
        }

        /** @param input in the form of a URI string */
        @JvmStatic
        fun parseOrNull(input: String): GQLOperationPath? {
            return GQLOperationPathParser(input).orNull()
        }

        /** @param input in the form of a URI */
        @JvmStatic
        fun fromURIOrThrow(input: URI): GQLOperationPath {
            return GQLOperationPathParser.fromURI(input).orElseThrow()
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
     *
     * Represented by URI path segments `/user/transactions/[HttpResponseMessage]responseCode` in
     * URI form and a query/mutation/subscription graph structure:
     * ```
     * user(id: 123) {
     *     transactions(
     *       filter: {
     *         correlation_id: { eq: "82b1d1cd-8020-41f1-9536-dc143c320ff1" } @alias(name: "traceId")
     *       }
     *     ) {
     *         ... on HttpResponseMessage {
     *             responseCode
     *         }
     *         messageBody
     *     }
     * }
     * ```
     *
     * in GraphQL query form where the referent is the `responseCode` field on an inline fragment =>
     * `/user/transactions/[HttpResponseMessage]responseCode`
     */
    val selection: ImmutableList<SelectionSegment>

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
        return selection.isEmpty() && argument.isEmpty() && directive.isEmpty()
    }

    fun level(): Int = selection.size

    fun getParentPath(): Option<GQLOperationPath> {
        return when (
            val directiveNamePathPair: Pair<String, ImmutableList<String>>? = directive.orNull()
        ) {
            null -> {
                when (
                    val argumentNamePathPair: Pair<String, ImmutableList<String>>? =
                        argument.orNull()
                ) {
                    null -> {
                        if (selection.isNotEmpty()) {
                            // Case 1: Reference to a field, not an argument or directive
                            // => Parent is another field or root itself
                            transform { dropTailSelectionSegment() }.some()
                        } else {
                            // Case 2: Reference to root: Query object type
                            // => No parent to root
                            none()
                        }
                    }
                    else -> {
                        if (argumentNamePathPair.second.isNotEmpty()) {
                            // Case 3: Reference to a field within an argument value
                            // => Parent is another field within the argument value or the argument
                            // itself
                            transform { dropTailArgumentPathSegment() }.some()
                        } else {
                            // Case 4: Reference to an argument
                            // => Parent is the field on which the argument is found
                            transform { clearArgument() }.some()
                        }
                    }
                }
            }
            else -> {
                if (directiveNamePathPair.second.isNotEmpty()) {
                    // Case 5: Reference to a field within a directive value
                    // => Parent is another field within the directive value or the directive itself
                    transform { dropTailDirectivePathSegment() }.some()
                } else {
                    // Case 6: Reference to a directive
                    // => Parent is the argument or field on which this is a directive
                    transform { clearDirective() }.some()
                }
            }
        }
    }

    fun selectionReferent(): Boolean {
        return argument.isEmpty() && directive.isEmpty()
    }

    fun argumentReferent(): Boolean {
        return argument.isNotEmpty() && directive.isEmpty()
    }

    fun directiveReferent(): Boolean {
        return directive.isNotEmpty()
    }

    fun referentOnFragment(): Boolean

    fun referentOnInlineFragment(): Boolean

    fun referentOnFragmentSpread(): Boolean

    fun referentAliased(): Boolean

    fun referentOnArgument(): Boolean {
        return argument.isNotEmpty()
    }

    fun referentOnDirective(): Boolean {
        return directive.isNotEmpty()
    }

    fun toDecodedURIString(): String

    override fun compareTo(other: GQLOperationPath): Int {
        return comparator().compare(this, other)
    }

    fun transform(mapper: Builder.() -> Builder): GQLOperationPath

    interface Builder {

        fun scheme(scheme: String): Builder

        fun prependField(vararg fieldName: String): Builder {
            return prependSelections(
                fieldName.asSequence().map(String::trim).map(::FieldSegment).toList()
            )
        }

        fun prependFields(fieldNames: List<String>): Builder {
            return prependSelections(
                fieldNames.asSequence().map(String::trim).map(::FieldSegment).toList()
            )
        }

        fun appendField(vararg fieldName: String): Builder {
            return appendSelections(
                fieldName.asSequence().map(String::trim).map(::FieldSegment).toList()
            )
        }

        fun appendFields(fieldNames: List<String>): Builder {
            return appendSelections(
                fieldNames.asSequence().map(String::trim).map(::FieldSegment).toList()
            )
        }

        fun field(vararg fieldName: String): Builder {
            return appendField(*fieldName)
        }

        fun fields(fieldNames: List<String>): Builder {
            return appendFields(fieldNames)
        }

        fun prependAliasedField(alias: String, fieldName: String): Builder {
            return prependSelection(
                AliasedFieldSegment(alias = alias.trim(), fieldName = fieldName.trim())
            )
        }

        fun appendAliasedField(alias: String, fieldName: String): Builder {
            return appendSelection(
                AliasedFieldSegment(alias = alias.trim(), fieldName = fieldName.trim())
            )
        }

        fun aliasedField(alias: String, fieldName: String): Builder {
            return appendAliasedField(alias = alias.trim(), fieldName = fieldName.trim())
        }

        fun prependInlineFragment(typeName: String, fieldName: String): Builder {
            return prependSelection(
                InlineFragmentSegment(
                    typeName = typeName.trim(),
                    selectedField = FieldSegment(fieldName = fieldName.trim())
                )
            )
        }

        fun prependInlineFragment(typeName: String, alias: String, fieldName: String): Builder {
            return prependSelection(
                InlineFragmentSegment(
                    typeName = typeName.trim(),
                    selectedField =
                        AliasedFieldSegment(alias = alias.trim(), fieldName = fieldName.trim())
                )
            )
        }

        fun appendInlineFragment(typeName: String, fieldName: String): Builder {
            return appendSelection(
                InlineFragmentSegment(
                    typeName = typeName.trim(),
                    selectedField = FieldSegment(fieldName = fieldName.trim())
                )
            )
        }

        fun appendInlineFragment(typeName: String, alias: String, fieldName: String): Builder {
            return appendSelection(
                InlineFragmentSegment(
                    typeName = typeName.trim(),
                    selectedField =
                        AliasedFieldSegment(alias = alias.trim(), fieldName = fieldName.trim())
                )
            )
        }

        fun inlineFragment(typeName: String, fieldName: String): Builder {
            return appendInlineFragment(typeName = typeName, fieldName = fieldName)
        }

        fun inlineFragment(typeName: String, alias: String, fieldName: String): Builder {
            return appendInlineFragment(typeName = typeName, alias = alias, fieldName = fieldName)
        }

        fun prependFragmentSpread(
            fragmentName: String,
            typeName: String,
            fieldName: String
        ): Builder {
            return prependSelection(
                FragmentSpreadSegment(
                    fragmentName = fragmentName.trim(),
                    typeName = typeName.trim(),
                    selectedField = FieldSegment(fieldName = fieldName.trim())
                )
            )
        }

        fun prependFragmentSpread(
            fragmentName: String,
            typeName: String,
            alias: String,
            fieldName: String
        ): Builder {
            return prependSelection(
                FragmentSpreadSegment(
                    fragmentName = fragmentName.trim(),
                    typeName = typeName.trim(),
                    selectedField =
                        AliasedFieldSegment(alias = alias.trim(), fieldName = fieldName.trim())
                )
            )
        }

        fun appendFragmentSpread(
            fragmentName: String,
            typeName: String,
            fieldName: String
        ): Builder {
            return appendSelection(
                FragmentSpreadSegment(
                    fragmentName = fragmentName.trim(),
                    typeName = typeName.trim(),
                    selectedField = FieldSegment(fieldName = fieldName.trim())
                )
            )
        }

        fun appendFragmentSpread(
            fragmentName: String,
            typeName: String,
            alias: String,
            fieldName: String
        ): Builder {
            return appendSelection(
                FragmentSpreadSegment(
                    fragmentName = fragmentName.trim(),
                    typeName = typeName.trim(),
                    selectedField =
                        AliasedFieldSegment(alias = alias.trim(), fieldName = fieldName.trim())
                )
            )
        }

        fun fragmentSpread(fragmentName: String, typeName: String, fieldName: String): Builder {
            return appendFragmentSpread(
                fragmentName = fragmentName.trim(),
                typeName = typeName.trim(),
                fieldName = fieldName.trim()
            )
        }

        fun fragmentSpread(
            fragmentName: String,
            typeName: String,
            alias: String,
            fieldName: String
        ): Builder {
            return appendFragmentSpread(
                fragmentName = fragmentName,
                typeName = typeName,
                alias = alias,
                fieldName = fieldName
            )
        }

        fun prependSelection(vararg selectionSegment: SelectionSegment): Builder

        fun prependSelections(selectionSegments: List<SelectionSegment>): Builder

        fun appendSelection(vararg selectionSegment: SelectionSegment): Builder

        fun appendSelections(selectionSegments: List<SelectionSegment>): Builder

        fun selection(vararg selectionSegment: SelectionSegment): Builder {
            return appendSelection(*selectionSegment)
        }

        fun selections(selectionSegments: List<SelectionSegment>): Builder {
            return appendSelections(selectionSegments)
        }

        fun dropHeadSelectionSegment(): Builder

        fun dropTailSelectionSegment(): Builder

        fun clearSelection(): Builder

        fun argument(name: String, pathSegments: List<String>): Builder

        fun argument(name: String, vararg pathSegment: String): Builder

        fun prependArgumentPathSegment(vararg pathSegment: String): Builder

        fun prependArgumentPathSegments(pathSegments: List<String>): Builder

        fun appendArgumentPathSegment(vararg pathSegment: String): Builder

        fun appendArgumentPathSegments(pathSegments: List<String>): Builder

        fun dropHeadArgumentPathSegment(): Builder

        fun dropTailArgumentPathSegment(): Builder

        fun clearArgument(): Builder

        fun directive(name: String, pathSegments: List<String>): Builder

        fun directive(name: String, vararg pathSegment: String): Builder

        fun prependDirectivePathSegment(vararg pathSegment: String): Builder

        fun prependDirectivePathSegments(pathSegments: List<String>): Builder

        fun appendDirectivePathSegment(vararg pathSegment: String): Builder

        fun appendDirectivePathSegments(pathSegments: List<String>): Builder

        fun dropHeadDirectivePathSegment(): Builder

        fun dropTailDirectivePathSegment(): Builder

        fun clearDirective(): Builder

        fun build(): GQLOperationPath
    }
}
