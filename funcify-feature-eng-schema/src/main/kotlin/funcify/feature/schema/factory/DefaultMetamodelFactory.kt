package funcify.feature.schema.factory

import funcify.feature.schema.Metamodel
import funcify.feature.schema.MetamodelFactory
import funcify.feature.schema.dataelement.DataElementSource
import funcify.feature.schema.dataelement.DataElementSourceProvider
import funcify.feature.schema.feature.FeatureCalculator
import funcify.feature.schema.feature.FeatureCalculatorProvider
import funcify.feature.schema.transformer.TransformerSource
import funcify.feature.schema.transformer.TransformerSourceProvider
import graphql.schema.idl.TypeDefinitionRegistry
import kotlinx.collections.immutable.PersistentMap
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-07-09
 */
internal class DefaultMetamodelFactory : MetamodelFactory {

    companion object {

        internal class DefaultBuilder(
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

                TODO("Not yet implemented")
            }
        }

        internal data class DefaultMetamodel(
            override val typeDefinitionRegistry: TypeDefinitionRegistry,
            override val dataElementSourcesByName: PersistentMap<String, DataElementSource>,
            override val dataElementSourceProvidersByName:
                PersistentMap<String, DataElementSourceProvider<*>>,
            override val transformerSourcesByName: PersistentMap<String, TransformerSource>,
            override val transformerSourceProvidersByName:
                PersistentMap<String, TransformerSourceProvider<*>>,
            override val featureCalculatorsByName: PersistentMap<String, FeatureCalculator>,
            override val featureCalculatorProvidersByName:
                PersistentMap<String, FeatureCalculatorProvider<*>>,
        ) : Metamodel {}
    }

    override fun builder(): Metamodel.Builder {
        TODO("Not yet implemented")
    }
}
