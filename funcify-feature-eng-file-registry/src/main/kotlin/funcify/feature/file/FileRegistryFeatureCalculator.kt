package funcify.feature.file

import funcify.feature.schema.feature.FeatureCalculator
import graphql.language.SDLDefinition
import kotlinx.collections.immutable.ImmutableSet

/**
 * @author smccarron
 * @created 2023-08-16
 */
interface FileRegistryFeatureCalculator : FeatureCalculator {

    override val name: String

    override val sourceSDLDefinitions: ImmutableSet<SDLDefinition<*>>
}
