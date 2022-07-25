package funcify.feature.datasource.metadata.alias

import arrow.core.Option
import funcify.feature.naming.ConventionalName
import funcify.feature.schema.vertex.ParameterAttributeVertex
import funcify.feature.schema.vertex.SourceAttributeVertex

/**
 *
 * @author smccarron
 * @created 2022-07-21
 */
interface AttributeAliasRegistry {

    companion object {

        fun newRegistry(): AttributeAliasRegistry {
            return DefaultAttributeAliasRegistry()
        }
    }

    fun registerSourceAttributeVertexWithAlias(
        sourceAttributeVertex: SourceAttributeVertex,
        alias: String
    ): AttributeAliasRegistry

    fun registerParameterAttributeVertexWithAlias(
        parameterAttributeVertex: ParameterAttributeVertex,
        alias: String
    ): AttributeAliasRegistry

    fun containsSimilarSourceAttributeNameOrAlias(name: String): Boolean {
        return getSourceAttributeVertexWithSimilarNameOrAlias(name).isDefined()
    }

    fun containsSimilarSourceAttributeNameOrAlias(conventionalName: ConventionalName): Boolean {
        return getSourceAttributeVertexWithSimilarNameOrAlias(conventionalName).isDefined()
    }

    fun containsSimilarParameterAttributeNameOrAlias(name: String): Boolean {
        return getParameterAttributeVertexWithSimilarNameOrAlias(name).isDefined()
    }

    fun containsSimilarParameterAttributeNameOrAlias(conventionalName: ConventionalName): Boolean {
        return getParameterAttributeVertexWithSimilarNameOrAlias(conventionalName).isDefined()
    }

    fun getSourceAttributeVertexWithSimilarNameOrAlias(name: String): Option<SourceAttributeVertex>

    fun getSourceAttributeVertexWithSimilarNameOrAlias(
        conventionalName: ConventionalName
    ): Option<SourceAttributeVertex>

    fun getParameterAttributeVertexWithSimilarNameOrAlias(
        name: String
    ): Option<ParameterAttributeVertex>

    fun getParameterAttributeVertexWithSimilarNameOrAlias(
        conventionalName: ConventionalName
    ): Option<ParameterAttributeVertex>
}
