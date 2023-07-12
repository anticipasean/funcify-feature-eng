package funcify.feature.schema.factory

import funcify.feature.schema.Metamodel
import funcify.feature.schema.MetamodelBuildStrategy
import funcify.feature.schema.MetamodelFactory
import funcify.feature.schema.context.DefaultMetamodelBuildContext
import funcify.feature.schema.dataelement.DataElementSourceProvider
import funcify.feature.schema.feature.FeatureCalculatorProvider
import funcify.feature.schema.transformer.TransformerSourceProvider
import kotlinx.collections.immutable.toPersistentList
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
                mutableListOf<DataElementSourceProvider<*>>(),
            private val transformerSourceProviders: MutableList<TransformerSourceProvider<*>> =
                mutableListOf<TransformerSourceProvider<*>>(),
            private val featureCalculatorProviders: MutableList<FeatureCalculatorProvider<*>> =
                mutableListOf<FeatureCalculatorProvider<*>>(),
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

            override fun build(): Mono<out Metamodel> {
                return metamodelBuildStrategy.buildMetamodel(
                    DefaultMetamodelBuildContext(
                        dataElementSourceProviders = dataElementSourceProviders.toPersistentList(),
                        transformerSourceProviders = transformerSourceProviders.toPersistentList(),
                        featureCalculatorProviders = featureCalculatorProviders.toPersistentList()
                    )
                )
            }
        }
    }

    override fun builder(): Metamodel.Builder {
        return DefaultBuilder(metamodelBuildStrategy)
    }
}
