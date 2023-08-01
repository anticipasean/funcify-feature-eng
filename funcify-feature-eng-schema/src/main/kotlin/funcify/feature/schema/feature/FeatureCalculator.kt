package funcify.feature.schema.feature

import funcify.feature.schema.Source
import graphql.language.SDLDefinition
import kotlinx.collections.immutable.ImmutableSet

/**
 * @author smccarron
 * @created 2023-07-01
 */
interface FeatureCalculator : Source {

    companion object {
        const val FEATURE_STORE_NOT_PROVIDED: String = "<NA>"
        const val FEATURE_PUBLISHER_NOT_PROVIDED: String = "<NA>"
    }

    override val name: String

    val featureStoreName: String
        get() = FEATURE_STORE_NOT_PROVIDED

    val featurePublisherName: String
        get() = FEATURE_PUBLISHER_NOT_PROVIDED

    override val sourceSDLDefinitions: ImmutableSet<SDLDefinition<*>>
}
