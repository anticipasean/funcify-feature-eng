package funcify.feature.schema.transformer

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.path.operation.GQLOperationPath
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import kotlinx.collections.immutable.ImmutableMap
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-08-01
 */
interface TransformerCallable : (ImmutableMap<GQLOperationPath, JsonNode>) -> Mono<JsonNode> {

    val transformerFieldCoordinates: FieldCoordinates

    val transformerPath: GQLOperationPath

    val transformerGraphQLFieldDefinition: GraphQLFieldDefinition

    val argumentsByPath: ImmutableMap<GQLOperationPath, GraphQLArgument>

    override fun invoke(arguments: ImmutableMap<GQLOperationPath, JsonNode>): Mono<JsonNode>

    interface Builder {

        fun selectTransformer(
            coordinates: FieldCoordinates,
            path: GQLOperationPath,
            graphQLFieldDefinition: GraphQLFieldDefinition
        ): Builder

        fun build(): TransformerCallable
    }
}
