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
    (TrackableValue<JsonNode>, ImmutableMap<GQLOperationPath, Mono<JsonNode>>) -> Mono<
            TrackableValue<JsonNode>
        > {

    val featureCoordinates: FieldCoordinates

    val featurePath: GQLOperationPath

    val featureGraphQLFieldDefinition: GraphQLFieldDefinition

    val argumentsByPath: ImmutableMap<GQLOperationPath, GraphQLArgument>

    val transformerCallablesByPath: ImmutableMap<GQLOperationPath, TransformerCallable>

    interface Builder {

        fun setFeatureSelection(
            coordinates: FieldCoordinates,
            path: GQLOperationPath,
            graphQLFieldDefinition: GraphQLFieldDefinition
        ): Builder

        fun addArgument(path: GQLOperationPath, graphQLArgument: GraphQLArgument): Builder

        fun addAllArguments(arguments: Iterable<Pair<GQLOperationPath, GraphQLArgument>>): Builder

        fun addAllArguments(arguments: Map<GQLOperationPath, GraphQLArgument>): Builder

        fun addTransformerCallable(transformerCallable: TransformerCallable): Builder

        fun build(): FeatureCalculatorCallable
    }
}
