package funcify.feature.datasource.metadata.alias

import arrow.core.Option
import arrow.core.toOption
import funcify.feature.naming.ConventionalName
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.vertex.SourceAttributeVertex
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

internal data class DefaultAliasRegistry(
    private val sourceAttributeVerticesByStandardAndAliasQualifiedNames:
        PersistentMap<String, SourceAttributeVertex> =
        persistentMapOf(),
    private val memoizingAliasMapper: MemoizingAliasMapperFunction = MemoizingAliasMapperFunction()
) : AliasRegistry {

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
            private val schematicVerticesByNormalizedName:
                ConcurrentMap<String, SourceAttributeVertex> =
                ConcurrentHashMap(),
            private val sourceAttributeVerticesByStandardAndAliasQualifiedNames:
                PersistentMap<String, SourceAttributeVertex> =
                persistentMapOf()
        ) : (String) -> Option<SourceAttributeVertex> {

            override fun invoke(unnormalizedName: String): Option<SourceAttributeVertex> {
                return schematicVerticesByNormalizedName
                    .computeIfAbsent(
                        unnormalizedName,
                        ::normalizeNameAndAttemptToMapToSourceAttributeVertex
                    )
                    .toOption()
            }

            private fun normalizeNameAndAttemptToMapToSourceAttributeVertex(
                unnormalizedName: String
            ): SourceAttributeVertex? {
                if (unnormalizedName.length < 3) {
                    return null
                }
                return sourceAttributeVerticesByStandardAndAliasQualifiedNames[
                    StandardNamingConventions.SNAKE_CASE.deriveName(unnormalizedName).qualifiedForm]
            }
        }
    }

    override fun registerSourceAttributeVertexWithAlias(
        sourceAttributeVertex: SourceAttributeVertex,
        alias: String
    ): AliasRegistry {
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
            memoizingAliasMapper =
                MemoizingAliasMapperFunction(
                    sourceAttributeVerticesByStandardAndAliasQualifiedNames =
                        sourceAttributeVerticesByStandardAndAliasQualifiedNames
                )
        )
    }

    override fun getSourceAttributeVertexWithSimilarNameOrAlias(
        name: String
    ): Option<SourceAttributeVertex> {
        return memoizingAliasMapper.invoke(name)
    }

    override fun getSourceAttributeVertexWithSimilarNameOrAlias(
        conventionalName: ConventionalName
    ): Option<SourceAttributeVertex> {
        return if (
            conventionalName.namingConventionKey ==
                StandardNamingConventions.SNAKE_CASE.conventionKey
        ) {
            memoizingAliasMapper.invoke(conventionalName.qualifiedForm)
        } else {
            memoizingAliasMapper.invoke(conventionalName.nameSegments.joinToString("_"))
        }
    }
}
