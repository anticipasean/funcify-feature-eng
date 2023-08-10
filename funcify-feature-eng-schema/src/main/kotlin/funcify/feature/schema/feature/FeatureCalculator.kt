package funcify.feature.schema.feature

import funcify.feature.schema.Source
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.transformer.TransformerCallable
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

    fun builder(): Builder

    interface Builder {

        fun setFeaturePath(featurePath: GQLOperationPath): Builder

        fun addDataElementArgumentPath(argumentPath: GQLOperationPath): Builder

        fun addAllDataElementArgumentPaths(argumentPaths: Iterable<GQLOperationPath>): Builder

        fun addTransformerCallable(transformerCallable: TransformerCallable): Builder

        fun addTransformerCallables(transformerCallables: Iterable<TransformerCallable>): Builder

        fun build(): FeatureCalculatorCallable
    }
}
