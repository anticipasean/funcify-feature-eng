package funcify.feature.schema.document

import funcify.feature.schema.path.operation.GQLOperationPath
import graphql.language.Value
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet

/**
 * @author smccarron
 * @created 2023-10-12
 */
interface GQLDocumentSpec {

    val fieldPaths: ImmutableSet<GQLOperationPath>

    val argumentPathsByVariableName: ImmutableMap<String, ImmutableSet<GQLOperationPath>>

    val variableNameByArgumentPath: ImmutableMap<GQLOperationPath, String>

    val argumentDefaultLiteralValuesByPath: ImmutableMap<GQLOperationPath, Value<*>>

    fun update(transformer: Builder.() -> Builder): GQLDocumentSpec

    interface Builder {

        fun addFieldPath(fieldPath: GQLOperationPath): Builder

        fun addAllFieldPaths(fieldPaths: Iterable<GQLOperationPath>): Builder

        fun putArgumentPathForVariableName(
            variableName: String,
            argumentPath: GQLOperationPath
        ): Builder

        fun putAllArgumentPathsForVariableNames(
            variableNameArgumentPathPairs: Iterable<Pair<String, GQLOperationPath>>
        ): Builder

        fun putAllArgumentPathsForVariableNames(
            variableNameArgumentPathsMap: Map<String, Set<GQLOperationPath>>
        ): Builder

        fun putDefaultLiteralValueForArgumentPath(
            argumentPath: GQLOperationPath,
            defaultLiteralValue: Value<*>
        ): Builder

        fun putAllDefaultLiteralValuesForArgumentPaths(
            argumentPathDefaultLiteralValuePairs: Iterable<Pair<GQLOperationPath, Value<*>>>
        ): Builder

        fun putAllDefaultLiteralValuesForArgumentPaths(
            argumentPathDefaultLiteralValuesMap: Map<GQLOperationPath, Value<*>>
        ): Builder

        fun build(): GQLDocumentSpec
    }
}
