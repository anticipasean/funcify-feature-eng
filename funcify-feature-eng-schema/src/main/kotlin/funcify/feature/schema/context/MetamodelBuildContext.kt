package funcify.feature.schema.context

import funcify.feature.schema.dataelement.DataElementSource
import funcify.feature.schema.dataelement.DataElementSourceProvider
import funcify.feature.schema.feature.FeatureCalculator
import funcify.feature.schema.feature.FeatureCalculatorProvider
import funcify.feature.schema.transformer.TransformerSource
import funcify.feature.schema.transformer.TransformerSourceProvider
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

/**
 * @author smccarron
 * @created 2023-07-11
 */
interface MetamodelBuildContext {

    val dataElementSourceProviders: ImmutableList<DataElementSourceProvider<*>>

    val transformerSourceProviders: ImmutableList<TransformerSourceProvider<*>>

    val featureCalculatorProviders: ImmutableList<FeatureCalculatorProvider<*>>

    val transformerSourcesByName: ImmutableMap<String, TransformerSource>

    val dataElementSourcesByName: ImmutableMap<String, DataElementSource>

    val featureCalculatorsByName: ImmutableMap<String, FeatureCalculator>

    fun update(transformer: Builder.() -> Builder): MetamodelBuildContext

    interface Builder {

        fun addTransformerSourceProvider(
            transformerSourceProvider: TransformerSourceProvider<*>
        ): Builder

        fun addDataElementSourceProvider(
            dataElementSourceProvider: DataElementSourceProvider<*>
        ): Builder

        fun addFeatureCalculatorProvider(
            featureCalculatorProvider: FeatureCalculatorProvider<*>
        ): Builder

        fun addTransformerSource(transformerSource: TransformerSource): Builder

        fun addDataElementSource(dataElementSource: DataElementSource): Builder

        fun addFeatureCalculator(featureCalculator: FeatureCalculator): Builder

        fun build(): MetamodelBuildContext
    }
}
