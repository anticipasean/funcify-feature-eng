package funcify.feature.schema.factory

import funcify.feature.schema.Metamodel
import funcify.feature.schema.dataelement.DataElementSource
import funcify.feature.schema.dataelement.DataElementSourceProvider
import funcify.feature.schema.feature.FeatureCalculator
import funcify.feature.schema.feature.FeatureCalculatorProvider
import funcify.feature.schema.transformer.TransformerSource
import funcify.feature.schema.transformer.TransformerSourceProvider
import graphql.schema.idl.TypeDefinitionRegistry
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

internal data class DefaultMetamodel(
    override val dataElementSourceProvidersByName:
        PersistentMap<String, DataElementSourceProvider<*>> =
        persistentMapOf(),
    override val transformerSourceProvidersByName:
        PersistentMap<String, TransformerSourceProvider<*>> =
        persistentMapOf(),
    override val featureCalculatorProvidersByName:
        PersistentMap<String, FeatureCalculatorProvider<*>> =
        persistentMapOf(),
    override val typeDefinitionRegistry: TypeDefinitionRegistry = TypeDefinitionRegistry(),
    override val dataElementSourcesByName: PersistentMap<String, DataElementSource> =
        persistentMapOf(),
    override val transformerSourcesByName: PersistentMap<String, TransformerSource> =
        persistentMapOf(),
    override val featureCalculatorsByName: PersistentMap<String, FeatureCalculator> =
        persistentMapOf(),
) : Metamodel {}
