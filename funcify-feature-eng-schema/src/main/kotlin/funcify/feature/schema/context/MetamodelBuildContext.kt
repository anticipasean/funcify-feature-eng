package funcify.feature.schema.context

import funcify.feature.schema.dataelement.DataElementSource
import funcify.feature.schema.dataelement.DataElementSourceProvider
import funcify.feature.schema.directive.alias.AttributeAliasRegistry
import funcify.feature.schema.directive.identifier.EntityRegistry
import funcify.feature.schema.directive.temporal.LastUpdatedTemporalAttributePathRegistry
import funcify.feature.schema.feature.FeatureCalculator
import funcify.feature.schema.feature.FeatureCalculatorProvider
import funcify.feature.schema.feature.FeatureJsonValuePublisher
import funcify.feature.schema.feature.FeatureJsonValueStore
import funcify.feature.schema.transformer.TransformerSource
import funcify.feature.schema.transformer.TransformerSourceProvider
import graphql.schema.idl.TypeDefinitionRegistry
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

/**
 * @author smccarron
 * @created 2023-07-11
 */
interface MetamodelBuildContext {

    companion object {

        fun empty(): MetamodelBuildContext {
            return DefaultMetamodelBuildContext.empty()
        }
    }

    val dataElementSourceProviders: ImmutableList<DataElementSourceProvider<*>>

    val transformerSourceProviders: ImmutableList<TransformerSourceProvider<*>>

    val featureCalculatorProviders: ImmutableList<FeatureCalculatorProvider<*>>

    val featureJsonValueStoresByName: ImmutableMap<String, FeatureJsonValueStore>

    val featureJsonValuePublishersByName: ImmutableMap<String, FeatureJsonValuePublisher>

    val transformerSourceProvidersByName: ImmutableMap<String, TransformerSourceProvider<*>>

    val dataElementSourceProvidersByName: ImmutableMap<String, DataElementSourceProvider<*>>

    val featureCalculatorProvidersByName: ImmutableMap<String, FeatureCalculatorProvider<*>>

    val transformerSourcesByName: ImmutableMap<String, TransformerSource>

    val dataElementSourcesByName: ImmutableMap<String, DataElementSource>

    val featureCalculatorsByName: ImmutableMap<String, FeatureCalculator>

    val typeDefinitionRegistry: TypeDefinitionRegistry

    val attributeAliasRegistry: AttributeAliasRegistry

    val entityRegistry: EntityRegistry

    val lastUpdatedTemporalAttributePathRegistry: LastUpdatedTemporalAttributePathRegistry

    fun update(transformer: Builder.() -> Builder): MetamodelBuildContext

    interface Builder {

        fun addAllTransformerSourceProviders(
            transformerSourceProviders: Iterable<TransformerSourceProvider<*>>
        ): Builder {
            return transformerSourceProviders.fold(this) {
                b: Builder,
                tsp: TransformerSourceProvider<*> ->
                b.addTransformerSourceProvider(tsp)
            }
        }

        fun addTransformerSourceProvider(
            transformerSourceProvider: TransformerSourceProvider<*>
        ): Builder

        fun addAllDataElementSourceProviders(
            dataElementSourceProviders: Iterable<DataElementSourceProvider<*>>
        ): Builder {
            return dataElementSourceProviders.fold(this) {
                b: Builder,
                desp: DataElementSourceProvider<*> ->
                b.addDataElementSourceProvider(desp)
            }
        }

        fun addDataElementSourceProvider(
            dataElementSourceProvider: DataElementSourceProvider<*>
        ): Builder

        fun addAllFeatureCalculatorProviders(
            featureCalculatorProviders: Iterable<FeatureCalculatorProvider<*>>
        ): Builder {
            return featureCalculatorProviders.fold(this) {
                b: Builder,
                fcp: FeatureCalculatorProvider<*> ->
                b.addFeatureCalculatorProvider(fcp)
            }
        }

        fun addFeatureCalculatorProvider(
            featureCalculatorProvider: FeatureCalculatorProvider<*>
        ): Builder

        fun addTransformerSource(transformerSource: TransformerSource): Builder

        fun addDataElementSource(dataElementSource: DataElementSource): Builder

        fun addFeatureCalculator(featureCalculator: FeatureCalculator): Builder

        fun typeDefinitionRegistry(typeDefinitionRegistry: TypeDefinitionRegistry): Builder

        fun addAllFeatureJsonValueStores(
            featureJsonValueStores: Iterable<FeatureJsonValueStore>
        ): Builder {
            return featureJsonValueStores.fold(this) { b: Builder, fjvs: FeatureJsonValueStore ->
                b.addFeatureJsonValueStore(fjvs)
            }
        }

        fun addFeatureJsonValueStore(featureJsonValueStore: FeatureJsonValueStore): Builder

        fun addAllFeatureJsonValuePublishers(
            featureJsonValuePublishers: Iterable<FeatureJsonValuePublisher>
        ): Builder {
            return featureJsonValuePublishers.fold(this) {
                b: Builder,
                fjvp: FeatureJsonValuePublisher ->
                b.addFeatureJsonValuePublisher(fjvp)
            }
        }

        fun addFeatureJsonValuePublisher(
            featureJsonValuePublisher: FeatureJsonValuePublisher
        ): Builder

        fun attributeAliasRegistry(attributeAliasRegistry: AttributeAliasRegistry): Builder

        fun entityRegistry(entityRegistry: EntityRegistry): Builder

        fun lastUpdatedTemporalAttributePathRegistry(lastUpdatedTemporalAttributePathRegistry: LastUpdatedTemporalAttributePathRegistry): Builder

        fun build(): MetamodelBuildContext
    }
}