package funcify.feature.schema.factory

import funcify.feature.schema.FeatureEngineeringModel
import funcify.feature.schema.FeatureEngineeringModelBuildStrategy
import funcify.feature.schema.FeatureEngineeringModelFactory
import funcify.feature.schema.context.FeatureEngineeringModelBuildContext
import funcify.feature.schema.dataelement.DataElementSourceProvider
import funcify.feature.schema.feature.FeatureCalculatorProvider
import funcify.feature.schema.feature.FeatureJsonValuePublisher
import funcify.feature.schema.feature.FeatureJsonValueStore
import funcify.feature.schema.transformer.TransformerSourceProvider
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-07-09
 */
internal class DefaultFeatureEngineeringModelFactory(
    private val featureEngineeringModelBuildStrategy: FeatureEngineeringModelBuildStrategy
) : FeatureEngineeringModelFactory {

    companion object {

        internal class DefaultBuilder(
            private val featureEngineeringModelBuildStrategy: FeatureEngineeringModelBuildStrategy,
            private val dataElementSourceProviders: MutableList<DataElementSourceProvider<*>> =
                mutableListOf(),
            private val transformerSourceProviders: MutableList<TransformerSourceProvider<*>> =
                mutableListOf(),
            private val featureCalculatorProviders: MutableList<FeatureCalculatorProvider<*>> =
                mutableListOf(),
            private val featureJsonValueStores: MutableList<FeatureJsonValueStore> =
                mutableListOf(),
            private val featureJsonValuePublishers: MutableList<FeatureJsonValuePublisher> =
                mutableListOf()
        ) : FeatureEngineeringModel.Builder {

            override fun addDataElementSourceProvider(
                provider: DataElementSourceProvider<*>
            ): FeatureEngineeringModel.Builder {
                this.dataElementSourceProviders.add(provider)
                return this
            }

            override fun addTransformerSourceProvider(
                provider: TransformerSourceProvider<*>
            ): FeatureEngineeringModel.Builder {
                this.transformerSourceProviders.add(provider)
                return this
            }

            override fun addFeatureCalculatorProvider(
                provider: FeatureCalculatorProvider<*>
            ): FeatureEngineeringModel.Builder {
                this.featureCalculatorProviders.add(provider)
                return this
            }

            override fun addFeatureJsonValueStore(
                featureJsonValueStore: FeatureJsonValueStore
            ): FeatureEngineeringModel.Builder {
                this.featureJsonValueStores.add(featureJsonValueStore)
                return this
            }

            override fun addFeatureJsonValuePublisher(
                featureJsonValuePublisher: FeatureJsonValuePublisher
            ): FeatureEngineeringModel.Builder {
                this.featureJsonValuePublishers.add(featureJsonValuePublisher)
                return this
            }

            override fun build(): Mono<out FeatureEngineeringModel> {
                return featureEngineeringModelBuildStrategy.buildFeatureEngineeringModel(
                    FeatureEngineeringModelBuildContext.empty().update {
                        addAllTransformerSourceProviders(transformerSourceProviders)
                        addAllDataElementSourceProviders(dataElementSourceProviders)
                        addAllFeatureCalculatorProviders(featureCalculatorProviders)
                        addAllFeatureJsonValueStores(featureJsonValueStores)
                        addAllFeatureJsonValuePublishers(featureJsonValuePublishers)
                    }
                )
            }
        }
    }

    override fun builder(): FeatureEngineeringModel.Builder {
        return DefaultBuilder(featureEngineeringModelBuildStrategy)
    }
}
