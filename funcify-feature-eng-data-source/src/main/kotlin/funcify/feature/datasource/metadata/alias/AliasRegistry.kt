package funcify.feature.datasource.metadata.alias

import arrow.core.Option
import funcify.feature.naming.ConventionalName
import funcify.feature.schema.vertex.SourceAttributeVertex

/**
 *
 * @author smccarron
 * @created 2022-07-21
 */
interface AliasRegistry {

    companion object {

        fun newRegistry(): AliasRegistry {
            return DefaultAliasRegistry()
        }
    }

    fun registerSourceAttributeVertexWithAlias(
        sourceAttributeVertex: SourceAttributeVertex,
        alias: String
    ): AliasRegistry

    fun containsSimilarNameOrAlias(name: String): Boolean {
        return getSourceAttributeVertexWithSimilarNameOrAlias(name).isDefined()
    }

    fun containsSimilarNameOrAlias(conventionalName: ConventionalName): Boolean {
        return getSourceAttributeVertexWithSimilarNameOrAlias(conventionalName).isDefined()
    }

    fun getSourceAttributeVertexWithSimilarNameOrAlias(name: String): Option<SourceAttributeVertex>

    fun getSourceAttributeVertexWithSimilarNameOrAlias(
        conventionalName: ConventionalName
    ): Option<SourceAttributeVertex>
}
