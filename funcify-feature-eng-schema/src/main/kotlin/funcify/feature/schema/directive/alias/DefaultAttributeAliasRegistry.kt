package funcify.feature.schema.directive.alias

import arrow.core.Option
import arrow.core.toOption
import funcify.feature.naming.ConventionalName
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.path.SchematicPath
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

internal data class DefaultAttributeAliasRegistry(
    private val sourceAttributeVerticesByStandardAndAliasQualifiedNames:
        PersistentMap<String, SchematicPath> =
        persistentMapOf(),
    private val parameterAttributeVerticesByStandardAndAliasQualifiedNames:
        PersistentMap<String, SchematicPath> =
        persistentMapOf(),
    private val memoizingSourceAttributeVertexAliasMapper: MemoizingAliasMapperFunction =
        MemoizingAliasMapperFunction(),
    private val memoizingParameterAttributeVertexAliasMapper: MemoizingAliasMapperFunction =
        MemoizingAliasMapperFunction()
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
    }

    override fun registerSourceVertexPathWithAlias(
        sourceVertexPath: SchematicPath,
        alias: String
    ): AttributeAliasRegistry {
        if (sourceVertexPath.arguments.isNotEmpty()) {
            return registerParameterVertexPathWithAlias(sourceVertexPath, alias)
        }
        val aliasQualifiedName: String =
            StandardNamingConventions.SNAKE_CASE.deriveName(alias).qualifiedForm
        val updatedSourceAttributeVerticesByQualifiedNames: PersistentMap<String, SchematicPath> =
            sequenceOf(aliasQualifiedName)
                .flatMap { s ->
                    if (s.indexOf('_') >= 0) {
                        sequenceOf(s.replace("_", ""), s)
                    } else {
                        sequenceOf(s)
                    }
                }
                .fold(sourceAttributeVerticesByStandardAndAliasQualifiedNames) { pm, name ->
                    pm.put(name, sourceVertexPath)
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
        if (parameterVertexPath.arguments.isEmpty()) {
            return registerSourceVertexPathWithAlias(parameterVertexPath, alias)
        }
        val aliasQualifiedName: String =
            StandardNamingConventions.SNAKE_CASE.deriveName(alias).qualifiedForm
        val updatedParameterAttributeVerticesByQualifiedNames:
            PersistentMap<String, SchematicPath> =
            sequenceOf(aliasQualifiedName)
                .flatMap { s ->
                    if (s.indexOf('_') >= 0) {
                        sequenceOf(s.replace("_", ""), s)
                    } else {
                        sequenceOf(s)
                    }
                }
                .fold(parameterAttributeVerticesByStandardAndAliasQualifiedNames) { pm, name ->
                    pm.put(name, parameterVertexPath)
                }

        return copy(
            parameterAttributeVerticesByStandardAndAliasQualifiedNames =
                updatedParameterAttributeVerticesByQualifiedNames,
            // Wipe cache clean when adding new entry in order to maintain mappings
            // only between the latest entry set and input strings
            memoizingParameterAttributeVertexAliasMapper =
                MemoizingAliasMapperFunction(
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
                conventionalName.nameSegments.joinToString("_")
            )
        }
    }

    override fun getParameterVertexPathWithSimilarNameOrAlias(name: String): Option<SchematicPath> {
        return memoizingParameterAttributeVertexAliasMapper.invoke(name)
    }

    override fun getParameterVertexPathWithSimilarNameOrAlias(
        conventionalName: ConventionalName
    ): Option<SchematicPath> {
        return if (
            conventionalName.namingConventionKey ==
                StandardNamingConventions.SNAKE_CASE.conventionKey
        ) {
            memoizingParameterAttributeVertexAliasMapper.invoke(conventionalName.qualifiedForm)
        } else {
            memoizingParameterAttributeVertexAliasMapper.invoke(
                conventionalName.nameSegments.joinToString("_")
            )
        }
    }
}
