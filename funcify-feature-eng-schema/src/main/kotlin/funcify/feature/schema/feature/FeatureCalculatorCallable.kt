package funcify.feature.schema.feature

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.tracking.TrackableValue
import funcify.feature.schema.transformer.TransformerCallable
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import kotlinx.collections.immutable.ImmutableMap
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-08-01
 */
interface FeatureCalculatorCallable :
    (TrackableValue<JsonNode>, ImmutableMap<GQLOperationPath, Mono<out JsonNode>>) -> Mono<
            out TrackableValue<JsonNode>
        > {

    val featureSpecifiedFeatureCalculator: FeatureSpecifiedFeatureCalculator

    val featureCoordinates: FieldCoordinates
        get() = featureSpecifiedFeatureCalculator.featureFieldCoordinates

    val featurePath: GQLOperationPath
        get() = featureSpecifiedFeatureCalculator.featurePath

    val featureGraphQLFieldDefinition: GraphQLFieldDefinition
        get() = featureSpecifiedFeatureCalculator.featureFieldDefinition

    val argumentsByName: ImmutableMap<String, GraphQLArgument>
        get() = featureSpecifiedFeatureCalculator.argumentsByName

    val argumentsByPath: ImmutableMap<GQLOperationPath, GraphQLArgument>
        get() = featureSpecifiedFeatureCalculator.argumentsByPath

    val transformerCallable: TransformerCallable

    override fun invoke(
        trackableFeatureValue: TrackableValue<JsonNode>,
        arguments: ImmutableMap<GQLOperationPath, Mono<out JsonNode>>
    ): Mono<out TrackableValue<JsonNode>>

    interface Builder {

        fun selectFeature(
            featureSpecifiedFeatureCalculator: FeatureSpecifiedFeatureCalculator
        ): Builder

        fun setTransformerCallable(transformerCallable: TransformerCallable): Builder

        fun build(): FeatureCalculatorCallable
    }
}
