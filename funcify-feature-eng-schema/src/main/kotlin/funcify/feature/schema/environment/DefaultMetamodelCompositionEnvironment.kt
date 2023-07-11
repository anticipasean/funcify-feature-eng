package funcify.feature.schema.environment

import funcify.feature.schema.dataelement.DataElementSourceProvider
import funcify.feature.schema.feature.FeatureCalculatorProvider
import funcify.feature.schema.transformer.TransformerSourceProvider

internal data class DefaultMetamodelCompositionEnvironment(
    override val dataElementSourceProviders: List<DataElementSourceProvider<*>> = listOf(),
    override val transformerSourceProviders: List<TransformerSourceProvider<*>> = listOf(),
    override val featureCalculatorProviders: List<FeatureCalculatorProvider<*>> = listOf(),
) : MetamodelCompositionEnvironment {}
