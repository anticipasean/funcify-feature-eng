package funcify.feature.schema.directive.alias

import arrow.core.Option
import arrow.core.identity
import arrow.core.toOption
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import funcify.feature.naming.ConventionalName
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.path.SchematicPath
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

internal data class DefaultAttributeAliasRegistry(
    private val sourceAttributeVerticesByStandardAndAliasQualifiedNames:
        PersistentMap<String, SchematicPath> =
        persistentMapOf(),
    private val parameterAttributeVerticesByStandardAndAliasQualifiedNames:
        PersistentMap<String, PersistentSet<SchematicPath>> =
        persistentMapOf(),
    private val memoizingSourceAttributeVertexAliasMapper: MemoizingAliasMapperFunction =
        MemoizingAliasMapperFunction(),
    private val memoizingParameterAttributeVertexAliasMapper: MemoizingAliasSetMapperFunction =
        MemoizingAliasSetMapperFunction()
) : AttributeAliasRegistry {

    companion object {

        private const val PREFIX_SIZE: Int = 3

        private fun String.prefixSignature(): Int {
            return when (this.length) {
                0 -> 0
                1 -> this.lowercase().hashCode()
                2 -> this.lowercase().hashCode()
                else ->
                    this.subSequence(0, PREFIX_SIZE)
                        .asSequence()
                        .map { c: Char -> c.lowercaseChar() }
                        .sorted()
                        .joinToString()
                        .hashCode()
            }
        }

        internal data class MemoizingAliasMapperFunction(
            private val schematicVerticesByNormalizedName: ConcurrentMap<String, SchematicPath> =
                ConcurrentHashMap(),
            private val verticesByStandardAndAliasQualifiedNames:
                PersistentMap<String, SchematicPath> =
                persistentMapOf()
        ) : (String) -> Option<SchematicPath> {

            override fun invoke(unnormalizedName: String): Option<SchematicPath> {
                return schematicVerticesByNormalizedName
                    .computeIfAbsent(unnormalizedName, ::normalizeNameAndAttemptToMapToVertex)
                    .toOption()
            }

            private fun normalizeNameAndAttemptToMapToVertex(
                unnormalizedName: String
            ): SchematicPath? {
                if (unnormalizedName.length < 3) {
                    return null
                }
                return verticesByStandardAndAliasQualifiedNames[
                    StandardNamingConventions.SNAKE_CASE.deriveName(unnormalizedName).qualifiedForm]
            }
        }

        internal data class MemoizingAliasSetMapperFunction(
            private val schematicVerticesByNormalizedName:
                ConcurrentMap<String, PersistentSet<SchematicPath>> =
                ConcurrentHashMap(),
            private val verticesByStandardAndAliasQualifiedNames:
                PersistentMap<String, PersistentSet<SchematicPath>> =
                persistentMapOf()
        ) : (String) -> ImmutableSet<SchematicPath> {

            override fun invoke(unnormalizedName: String): ImmutableSet<SchematicPath> {
                return schematicVerticesByNormalizedName.computeIfAbsent(
                    unnormalizedName,
                    ::normalizeNameAndAttemptToMapToVertex
                )
            }

            private fun normalizeNameAndAttemptToMapToVertex(
                unnormalizedName: String
            ): PersistentSet<SchematicPath> {
                if (unnormalizedName.length < 3) {
                    return persistentSetOf()
                }
                return verticesByStandardAndAliasQualifiedNames[
                        StandardNamingConventions.SNAKE_CASE.deriveName(unnormalizedName)
                            .qualifiedForm]
                    .toOption()
                    .fold(::persistentSetOf, ::identity)
            }
        }
    }

    override fun registerSourceVertexPathWithAlias(
        sourceVertexPath: SchematicPath,
        alias: String
    ): AttributeAliasRegistry {
        if (sourceVertexPath.argument.isNotEmpty()) {
            return registerParameterVertexPathWithAlias(sourceVertexPath, alias)
        }
        val aliasQualifiedName: String =
            StandardNamingConventions.SNAKE_CASE.deriveName(alias).qualifiedForm
        val updatedSourceAttributeVerticesByQualifiedNames: PersistentMap<String, SchematicPath> =
            sequenceOf(aliasQualifiedName).fold(
                sourceAttributeVerticesByStandardAndAliasQualifiedNames
            ) { pm, name ->
                if (name in pm) {
                    pm[name]
                        .toOption()
                        .filter { currentPathEntry ->
                            // current_path is not canonical if source_vertex_path input
                            // represents
                            // a shorter way to get to the same value
                            currentPathEntry.level() > sourceVertexPath.level()
                        }
                        .fold({ pm }, { pm.put(name, sourceVertexPath) })
                } else {
                    pm.put(name, sourceVertexPath)
                }
            }
        return copy(
            sourceAttributeVerticesByStandardAndAliasQualifiedNames =
                updatedSourceAttributeVerticesByQualifiedNames,
            // Wipe cache clean when adding new entry in order to maintain mappings
            // only between the latest entry set and input strings
            memoizingSourceAttributeVertexAliasMapper =
                MemoizingAliasMapperFunction(
                    verticesByStandardAndAliasQualifiedNames =
                        updatedSourceAttributeVerticesByQualifiedNames
                )
        )
    }

    override fun registerParameterVertexPathWithAlias(
        parameterVertexPath: SchematicPath,
        alias: String,
    ): AttributeAliasRegistry {
        if (parameterVertexPath.argument.isEmpty()) {
            return registerSourceVertexPathWithAlias(parameterVertexPath, alias)
        }
        val aliasQualifiedName: String =
            StandardNamingConventions.SNAKE_CASE.deriveName(alias).qualifiedForm
        val updatedParameterAttributeVerticesByQualifiedNames:
            PersistentMap<String, PersistentSet<SchematicPath>> =
            sequenceOf(aliasQualifiedName).fold(
                parameterAttributeVerticesByStandardAndAliasQualifiedNames
            ) { pm, name ->
                pm.put(name, pm.getOrElse(name) { persistentSetOf() }.add(parameterVertexPath))
            }

        return copy(
            parameterAttributeVerticesByStandardAndAliasQualifiedNames =
                updatedParameterAttributeVerticesByQualifiedNames,
            // Wipe cache clean when adding new entry in order to maintain mappings
            // only between the latest entry set and input strings
            memoizingParameterAttributeVertexAliasMapper =
                MemoizingAliasSetMapperFunction(
                    verticesByStandardAndAliasQualifiedNames =
                        updatedParameterAttributeVerticesByQualifiedNames
                )
        )
    }

    override fun getSourceVertexPathWithSimilarNameOrAlias(name: String): Option<SchematicPath> {
        return memoizingSourceAttributeVertexAliasMapper.invoke(name)
    }

    override fun getSourceVertexPathWithSimilarNameOrAlias(
        conventionalName: ConventionalName
    ): Option<SchematicPath> {
        return if (
            conventionalName.namingConventionKey ==
                StandardNamingConventions.SNAKE_CASE.conventionKey
        ) {
            memoizingSourceAttributeVertexAliasMapper.invoke(conventionalName.qualifiedForm)
        } else {
            memoizingSourceAttributeVertexAliasMapper.invoke(
                conventionalName.nameSegments.joinToString("_") { ns -> ns.value.lowercase() }
            )
        }
    }

    override fun getParameterVertexPathsWithSimilarNameOrAlias(
        name: String
    ): ImmutableSet<SchematicPath> {
        return memoizingParameterAttributeVertexAliasMapper.invoke(name)
    }

    override fun getParameterVertexPathsWithSimilarNameOrAlias(
        conventionalName: ConventionalName
    ): ImmutableSet<SchematicPath> {
        return if (
            conventionalName.namingConventionKey ==
                StandardNamingConventions.SNAKE_CASE.conventionKey
        ) {
            memoizingParameterAttributeVertexAliasMapper.invoke(conventionalName.qualifiedForm)
        } else {
            memoizingParameterAttributeVertexAliasMapper.invoke(
                conventionalName.nameSegments.joinToString("_") { ns -> ns.value.lowercase() }
            )
        }
    }

    private val computedStringForm: String by lazy {
        parameterAttributeVerticesByStandardAndAliasQualifiedNames
            .asSequence()
            .fold(
                sourceAttributeVerticesByStandardAndAliasQualifiedNames.asSequence().fold(
                    JsonNodeFactory.instance.objectNode()
                ) { on: ObjectNode, (name: String, path: SchematicPath) ->
                    on.put(name, path.toString())
                }
            ) { on: ObjectNode, (name: String, paths: PersistentSet<SchematicPath>) ->
                on.set<ObjectNode>(
                    name,
                    paths.asSequence().fold(JsonNodeFactory.instance.arrayNode()) {
                        an: ArrayNode,
                        p: SchematicPath ->
                        an.add(p.toString())
                    }
                )
            }
            .toString()
    }

    override fun toString(): String {
        return computedStringForm
    }
}
