package funcify.feature.schema.directive.alias

import arrow.core.Option
import funcify.feature.naming.ConventionalName
import funcify.feature.schema.path.operation.GQLOperationPath
import kotlinx.collections.immutable.ImmutableSet

/**
 * @author smccarron
 * @created 2022-07-21
 */
interface AttributeAliasRegistry {

    companion object {

        @JvmStatic
        fun newRegistry(): AttributeAliasRegistry {
            return DefaultAttributeAliasRegistry()
        }
    }

    fun registerSourceVertexPathWithAlias(
        sourceVertexPath: GQLOperationPath,
        alias: String
    ): AttributeAliasRegistry

    fun registerParameterVertexPathWithAlias(
        parameterVertexPath: GQLOperationPath,
        alias: String
    ): AttributeAliasRegistry

    fun containsSimilarSourceAttributeNameOrAlias(name: String): Boolean {
        return getSourceVertexPathWithSimilarNameOrAlias(name).isDefined()
    }

    fun containsSimilarSourceAttributeNameOrAlias(conventionalName: ConventionalName): Boolean {
        return getSourceVertexPathWithSimilarNameOrAlias(conventionalName).isDefined()
    }

    fun containsSimilarParameterAttributeNameOrAlias(name: String): Boolean {
        return getParameterVertexPathsWithSimilarNameOrAlias(name).isNotEmpty()
    }

    fun containsSimilarParameterAttributeNameOrAlias(conventionalName: ConventionalName): Boolean {
        return getParameterVertexPathsWithSimilarNameOrAlias(conventionalName).isNotEmpty()
    }

    fun getSourceVertexPathWithSimilarNameOrAlias(name: String): Option<GQLOperationPath>

    fun getSourceVertexPathWithSimilarNameOrAlias(
        conventionalName: ConventionalName
    ): Option<GQLOperationPath>

    fun getParameterVertexPathsWithSimilarNameOrAlias(name: String): ImmutableSet<GQLOperationPath>

    fun getParameterVertexPathsWithSimilarNameOrAlias(
        conventionalName: ConventionalName
    ): ImmutableSet<GQLOperationPath>
}
