package funcify.feature.file.source

import funcify.feature.file.FileRegistryFeatureCalculator
import funcify.feature.file.source.callable.DefaultFeatureCalculatorCallableBuilder
import funcify.feature.schema.feature.FeatureCalculatorCallable
import graphql.language.SDLDefinition
import kotlinx.collections.immutable.PersistentSet

internal class DefaultFileRegistryFeatureCalculator(
    override val name: String,
    override val sourceSDLDefinitions: PersistentSet<SDLDefinition<*>>
) : FileRegistryFeatureCalculator {

    override fun builder(): FeatureCalculatorCallable.Builder {
        return DefaultFeatureCalculatorCallableBuilder()
    }
}
