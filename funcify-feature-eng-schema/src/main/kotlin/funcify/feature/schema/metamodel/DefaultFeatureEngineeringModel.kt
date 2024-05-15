package funcify.feature.schema.metamodel

import funcify.feature.scalar.registry.ScalarTypeRegistry
import funcify.feature.schema.FeatureEngineeringModel
import funcify.feature.schema.dataelement.DataElementSource
import funcify.feature.schema.dataelement.DataElementSourceProvider
import funcify.feature.schema.feature.FeatureCalculator
import funcify.feature.schema.feature.FeatureCalculatorProvider
import funcify.feature.schema.feature.FeatureJsonValuePublisher
import funcify.feature.schema.feature.FeatureJsonValueStore
import funcify.feature.schema.limit.ModelLimits
import funcify.feature.schema.transformer.TransformerSource
import funcify.feature.schema.transformer.TransformerSourceProvider
import graphql.language.SDLDefinition
import graphql.schema.FieldCoordinates
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet

internal data class DefaultFeatureEngineeringModel(
    override val transformerFieldCoordinates: FieldCoordinates,
    override val dataElementFieldCoordinates: FieldCoordinates,
    override val featureFieldCoordinates: FieldCoordinates,
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
    override val scalarTypeRegistry: ScalarTypeRegistry,
    override val modelDefinitions: PersistentSet<SDLDefinition<*>>,
    override val modelLimits: ModelLimits,
) : FeatureEngineeringModel
