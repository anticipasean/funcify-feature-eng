package funcify.feature.schema.metamodel

import funcify.feature.schema.Metamodel
import funcify.feature.schema.dataelement.DataElementSource
import funcify.feature.schema.dataelement.DataElementSourceProvider
import funcify.feature.schema.feature.FeatureCalculator
import funcify.feature.schema.feature.FeatureCalculatorProvider
import funcify.feature.schema.feature.FeatureJsonValuePublisher
import funcify.feature.schema.feature.FeatureJsonValueStore
import funcify.feature.schema.transformer.TransformerSource
import funcify.feature.schema.transformer.TransformerSourceProvider
import graphql.schema.idl.TypeDefinitionRegistry
import kotlinx.collections.immutable.PersistentMap

internal data class DefaultMetamodel(
    override val dataElementSourceProvidersByName:
        PersistentMap<String, DataElementSourceProvider<*>>,
    override val transformerSourceProvidersByName:
        PersistentMap<String, TransformerSourceProvider<*>>,
    override val featureCalculatorProvidersByName:
        PersistentMap<String, FeatureCalculatorProvider<*>>,
    override val featureJsonValueStoresByName: PersistentMap<String, FeatureJsonValueStore>,
    override val featureJsonValuePublishersByName: PersistentMap<String, FeatureJsonValuePublisher>,
    override val dataElementSourcesByName: PersistentMap<String, DataElementSource>,
    override val transformerSourcesByName: PersistentMap<String, TransformerSource>,
    override val featureCalculatorsByName: PersistentMap<String, FeatureCalculator>,
    override val typeDefinitionRegistry: TypeDefinitionRegistry,
) : Metamodel {}
