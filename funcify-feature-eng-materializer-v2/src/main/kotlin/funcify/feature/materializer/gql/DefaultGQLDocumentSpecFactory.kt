package funcify.feature.materializer.gql

import arrow.core.foldLeft
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.language.Value
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet

/**
 * @author smccarron
 * @created 2023-10-12
 */
internal class DefaultGQLDocumentSpecFactory : GQLDocumentSpecFactory {

    companion object {

        internal class DefaultBuilder(
            private val existingSpec: GQLDocumentSpec? = null,
            private val fieldPaths: PersistentSet.Builder<GQLOperationPath> =
                existingSpec?.fieldPaths?.toPersistentSet()?.builder()
                    ?: persistentSetOf<GQLOperationPath>().builder(),
            private val argumentPathsByVariableName:
                PersistentMap.Builder<String, ImmutableSet<GQLOperationPath>> =
                existingSpec?.argumentPathsByVariableName?.toPersistentMap()?.builder()
                    ?: persistentMapOf<String, ImmutableSet<GQLOperationPath>>().builder(),
            private val argumentDefaultLiteralValuesByPath:
                PersistentMap.Builder<GQLOperationPath, Value<*>> =
                existingSpec?.argumentDefaultLiteralValuesByPath?.toPersistentMap()?.builder()
                    ?: persistentMapOf<GQLOperationPath, Value<*>>().builder(),
        ) : GQLDocumentSpec.Builder {

            override fun addFieldPath(fieldPath: GQLOperationPath): GQLDocumentSpec.Builder =
                this.apply {
                    require(fieldPath.refersToSelection()) {
                        """path refers to an argument, part of an argument, a directive, or part of a directive; 
                            |path must refer to a selection (field, inline_fragment, fragment_spread)"""
                            .flatten()
                    }
                    this.fieldPaths.add(fieldPath)
                }

            override fun addAllFieldPaths(
                fieldPaths: Iterable<GQLOperationPath>
            ): GQLDocumentSpec.Builder =
                this.apply {
                    fieldPaths.fold(this) { b: GQLDocumentSpec.Builder, p: GQLOperationPath ->
                        b.addFieldPath(p)
                    }
                }

            override fun putArgumentPathForVariableName(
                variableName: String,
                argumentPath: GQLOperationPath
            ): GQLDocumentSpec.Builder =
                this.apply {
                    require(
                        argumentPath.refersToArgument() ||
                            argumentPath.refersToObjectFieldWithinArgumentValue()
                    ) {
                        """path refers to selection (field, inline_fragment, fragment_spread) 
                            |or directive or part of a directive; 
                            |path must refer to an argument"""
                            .flatten()
                    }
                    this.argumentPathsByVariableName.put(
                        variableName,
                        this.argumentPathsByVariableName
                            .getOrElse(variableName, ::persistentSetOf)
                            .toPersistentSet()
                            .add(argumentPath)
                    )
                }

            override fun putAllArgumentPathsForVariableNames(
                variableNameArgumentPathPairs: Iterable<Pair<String, GQLOperationPath>>
            ): GQLDocumentSpec.Builder =
                this.apply {
                    variableNameArgumentPathPairs.fold(this) {
                        b: GQLDocumentSpec.Builder,
                        p: Pair<String, GQLOperationPath> ->
                        b.putArgumentPathForVariableName(p.first, p.second)
                    }
                }

            override fun putAllArgumentPathsForVariableNames(
                variableNameArgumentPathsMap: Map<String, Set<GQLOperationPath>>
            ): GQLDocumentSpec.Builder =
                this.apply {
                    variableNameArgumentPathsMap.foldLeft(this) {
                        b: GQLDocumentSpec.Builder,
                        (vn: String, ps: Set<GQLOperationPath>) ->
                        ps.fold(b) { b1, p: GQLOperationPath ->
                            b1.putArgumentPathForVariableName(vn, p)
                        }
                    }
                }

            override fun putDefaultLiteralValueForArgumentPath(
                argumentPath: GQLOperationPath,
                defaultLiteralValue: Value<*>,
            ): GQLDocumentSpec.Builder =
                this.apply {
                    require(
                        argumentPath.refersToArgument() ||
                            argumentPath.refersToObjectFieldWithinArgumentValue()
                    ) {
                        """path refers to selection (field, inline_fragment, fragment_spread) 
                            |or directive or part of a directive; 
                            |path must refer to an argument"""
                            .flatten()
                    }
                    this.argumentDefaultLiteralValuesByPath.put(argumentPath, defaultLiteralValue)
                }

            override fun putAllDefaultLiteralValuesForArgumentPaths(
                argumentPathDefaultLiteralValuePairs: Iterable<Pair<GQLOperationPath, Value<*>>>
            ): GQLDocumentSpec.Builder =
                this.apply {
                    argumentPathDefaultLiteralValuePairs.fold(this) {
                        b: GQLDocumentSpec.Builder,
                        (p: GQLOperationPath, v: Value<*>) ->
                        b.putDefaultLiteralValueForArgumentPath(p, v)
                    }
                }

            override fun putAllDefaultLiteralValuesForArgumentPaths(
                argumentPathDefaultLiteralValuesMap: Map<GQLOperationPath, Value<*>>
            ): GQLDocumentSpec.Builder =
                this.apply {
                    argumentPathDefaultLiteralValuesMap.foldLeft(this) {
                        b: GQLDocumentSpec.Builder,
                        (p: GQLOperationPath, v: Value<*>) ->
                        b.putDefaultLiteralValueForArgumentPath(p, v)
                    }
                }

            override fun build(): GQLDocumentSpec {
                return DefaultGQLDocumentSpec(
                    fieldPaths = this.fieldPaths.build(),
                    argumentPathsByVariableName = this.argumentPathsByVariableName.build(),
                    argumentDefaultLiteralValuesByPath =
                        this.argumentDefaultLiteralValuesByPath.build()
                )
            }
        }

        internal data class DefaultGQLDocumentSpec(
            override val fieldPaths: ImmutableSet<GQLOperationPath>,
            override val argumentPathsByVariableName:
                ImmutableMap<String, ImmutableSet<GQLOperationPath>>,
            override val argumentDefaultLiteralValuesByPath:
                ImmutableMap<GQLOperationPath, Value<*>>
        ) : GQLDocumentSpec {

            override val variableNameByArgumentPath:
                ImmutableMap<GQLOperationPath, String> by lazy {
                argumentPathsByVariableName
                    .asSequence()
                    .flatMap { (vn: String, ps: Set<GQLOperationPath>) ->
                        ps.asSequence().map { p: GQLOperationPath -> p to vn }
                    }
                    .reducePairsToPersistentMap()
            }

            override fun update(
                transformer: GQLDocumentSpec.Builder.() -> GQLDocumentSpec.Builder
            ): GQLDocumentSpec {
                return transformer.invoke(DefaultBuilder(this)).build()
            }
        }
    }

    override fun builder(): GQLDocumentSpec.Builder {
        return DefaultBuilder()
    }
}
