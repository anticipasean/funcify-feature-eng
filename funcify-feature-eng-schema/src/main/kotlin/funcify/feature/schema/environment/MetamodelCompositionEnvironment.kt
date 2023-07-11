package funcify.feature.schema.environment

import funcify.feature.schema.dataelement.DataElementSourceProvider
import funcify.feature.schema.feature.FeatureCalculatorProvider
import funcify.feature.schema.transformer.TransformerSourceProvider

/**
 * @author smccarron
 * @created 2023-07-11
 */
interface MetamodelCompositionEnvironment {

    val dataElementSourceProviders: List<DataElementSourceProvider<*>>

    val transformerSourceProviders: List<TransformerSourceProvider<*>>

    val featureCalculatorProviders: List<FeatureCalculatorProvider<*>>
}
