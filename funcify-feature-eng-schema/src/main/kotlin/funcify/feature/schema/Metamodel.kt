package funcify.feature.schema

import funcify.feature.schema.dataelement.DataElementSource
import funcify.feature.schema.dataelement.DataElementSourceProvider
import funcify.feature.schema.feature.FeatureCalculator
import funcify.feature.schema.feature.FeatureCalculatorProvider
import funcify.feature.schema.transformer.TransformerSource
import funcify.feature.schema.transformer.TransformerSourceProvider
import graphql.schema.idl.TypeDefinitionRegistry
import kotlinx.collections.immutable.ImmutableMap
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-06-30
 */
interface Metamodel {

    val typeDefinitionRegistry: TypeDefinitionRegistry

    val dataElementSourcesByName: ImmutableMap<String, DataElementSource>

    val dataElementSourceProvidersByName: ImmutableMap<String, DataElementSourceProvider<*>>

    val transformerSourcesByName: ImmutableMap<String, TransformerSource>

    val transformerSourceProvidersByName: ImmutableMap<String, TransformerSourceProvider<*>>

    val featureCalculatorsByName: ImmutableMap<String, FeatureCalculator>

    val featureCalculatorProvidersByName: ImmutableMap<String, FeatureCalculatorProvider<*>>

    interface Builder {

        fun addDataElementSourceProvider(provider: DataElementSourceProvider<*>): Builder

        fun addTransformerSourceProvider(provider: TransformerSourceProvider<*>): Builder

        fun addFeatureCalculatorProvider(provider: FeatureCalculatorProvider<*>): Builder

        fun build(): Mono<out Metamodel>
    }
}
