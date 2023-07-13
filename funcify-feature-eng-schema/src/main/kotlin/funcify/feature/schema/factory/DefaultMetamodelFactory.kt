package funcify.feature.schema.factory

import funcify.feature.schema.Metamodel
import funcify.feature.schema.MetamodelBuildStrategy
import funcify.feature.schema.MetamodelFactory
import funcify.feature.schema.context.MetamodelBuildContext
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
internal class DefaultMetamodelFactory(private val metamodelBuildStrategy: MetamodelBuildStrategy) :
    MetamodelFactory {

    companion object {

        internal class DefaultBuilder(
            private val metamodelBuildStrategy: MetamodelBuildStrategy,
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
        ) : Metamodel.Builder {

            override fun addDataElementSourceProvider(
                provider: DataElementSourceProvider<*>
            ): Metamodel.Builder {
                this.dataElementSourceProviders.add(provider)
                return this
            }

            override fun addTransformerSourceProvider(
                provider: TransformerSourceProvider<*>
            ): Metamodel.Builder {
                this.transformerSourceProviders.add(provider)
                return this
            }

            override fun addFeatureCalculatorProvider(
                provider: FeatureCalculatorProvider<*>
            ): Metamodel.Builder {
                this.featureCalculatorProviders.add(provider)
                return this
            }

            override fun addFeatureJsonValueStore(
                featureJsonValueStore: FeatureJsonValueStore
            ): Metamodel.Builder {
                this.featureJsonValueStores.add(featureJsonValueStore)
                return this
            }

            override fun addFeatureJsonValuePublisher(
                featureJsonValuePublisher: FeatureJsonValuePublisher
            ): Metamodel.Builder {
                this.featureJsonValuePublishers.add(featureJsonValuePublisher)
                return this
            }

            override fun build(): Mono<out Metamodel> {
                return metamodelBuildStrategy.buildMetamodel(
                    MetamodelBuildContext.empty().update {
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

    override fun builder(): Metamodel.Builder {
        return DefaultBuilder(metamodelBuildStrategy)
    }
}
