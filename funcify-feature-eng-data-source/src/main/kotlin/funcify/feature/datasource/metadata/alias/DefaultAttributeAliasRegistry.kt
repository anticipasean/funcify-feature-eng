package funcify.feature.datasource.metadata.alias

import arrow.core.Option
import arrow.core.toOption
import funcify.feature.naming.ConventionalName
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.vertex.ParameterAttributeVertex
import funcify.feature.schema.vertex.SourceAttributeVertex
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

internal data class DefaultAttributeAliasRegistry(
    private val sourceAttributeVerticesByStandardAndAliasQualifiedNames:
        PersistentMap<String, SourceAttributeVertex> =
        persistentMapOf(),
    private val parameterAttributeVerticesByStandardAndAliasQualifiedNames:
        PersistentMap<String, ParameterAttributeVertex> =
        persistentMapOf(),
    private val memoizingSourceAttributeVertexAliasMapper:
        MemoizingAliasMapperFunction<SourceAttributeVertex> =
        MemoizingAliasMapperFunction(),
    private val memoizingParameterAttributeVertexAliasMapper:
        MemoizingAliasMapperFunction<ParameterAttributeVertex> =
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

        internal data class MemoizingAliasMapperFunction<out V>(
            private val schematicVerticesByNormalizedName: ConcurrentMap<String, V> =
                ConcurrentHashMap(),
            private val verticesByStandardAndAliasQualifiedNames: PersistentMap<String, V> =
                persistentMapOf()
        ) : (String) -> Option<V> {

            override fun invoke(unnormalizedName: String): Option<V> {
                return schematicVerticesByNormalizedName
                    .computeIfAbsent(unnormalizedName, ::normalizeNameAndAttemptToMapToVertex)
                    .toOption()
            }

            private fun normalizeNameAndAttemptToMapToVertex(unnormalizedName: String): V? {
                if (unnormalizedName.length < 3) {
                    return null
                }
                return verticesByStandardAndAliasQualifiedNames[
                    StandardNamingConventions.SNAKE_CASE.deriveName(unnormalizedName).qualifiedForm]
            }
        }
    }

    override fun registerSourceAttributeVertexWithAlias(
        sourceAttributeVertex: SourceAttributeVertex,
        alias: String
    ): AttributeAliasRegistry {
        val sourceAttributeVertexQualifiedName: String =
            StandardNamingConventions.SNAKE_CASE.deriveName(
                    sourceAttributeVertex.compositeAttribute.conventionalName.qualifiedForm
                )
                .qualifiedForm
        val aliasQualifiedName: String =
            StandardNamingConventions.SNAKE_CASE.deriveName(alias).qualifiedForm
        val updatedSourceAttributeVerticesByQualifiedNames:
            PersistentMap<String, SourceAttributeVertex> =
            sourceAttributeVerticesByStandardAndAliasQualifiedNames
                .put(sourceAttributeVertexQualifiedName, sourceAttributeVertex)
                .put(aliasQualifiedName, sourceAttributeVertex)
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

    override fun registerParameterAttributeVertexWithAlias(
        parameterAttributeVertex: ParameterAttributeVertex,
        alias: String,
    ): AttributeAliasRegistry {
        val parameterAttributeVertexQualifiedName: String =
            StandardNamingConventions.SNAKE_CASE.deriveName(
                    parameterAttributeVertex.compositeParameterAttribute.conventionalName
                        .qualifiedForm
                )
                .qualifiedForm
        val aliasQualifiedName: String =
            StandardNamingConventions.SNAKE_CASE.deriveName(alias).qualifiedForm
        val updatedParameterAttributeVerticesByQualifiedNames:
            PersistentMap<String, ParameterAttributeVertex> =
            parameterAttributeVerticesByStandardAndAliasQualifiedNames
                .put(parameterAttributeVertexQualifiedName, parameterAttributeVertex)
                .put(aliasQualifiedName, parameterAttributeVertex)
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

    override fun getSourceAttributeVertexWithSimilarNameOrAlias(
        name: String
    ): Option<SourceAttributeVertex> {
        return memoizingSourceAttributeVertexAliasMapper.invoke(name)
    }

    override fun getSourceAttributeVertexWithSimilarNameOrAlias(
        conventionalName: ConventionalName
    ): Option<SourceAttributeVertex> {
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

    override fun getParameterAttributeVertexWithSimilarNameOrAlias(
        name: String
    ): Option<ParameterAttributeVertex> {
        return memoizingParameterAttributeVertexAliasMapper.invoke(name)
    }

    override fun getParameterAttributeVertexWithSimilarNameOrAlias(
        conventionalName: ConventionalName
    ): Option<ParameterAttributeVertex> {
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
