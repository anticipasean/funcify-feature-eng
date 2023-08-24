package funcify.feature.schema.dataelement

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
interface DataElementCallable : (ImmutableMap<GQLOperationPath, JsonNode>) -> Mono<JsonNode> {

    val domainFieldCoordinates: FieldCoordinates

    val domainPath: GQLOperationPath

    val domainGraphQLFieldDefinition: GraphQLFieldDefinition

    val argumentsByPath: ImmutableMap<GQLOperationPath, GraphQLArgument>

    val selectionsByPath: ImmutableMap<GQLOperationPath, GraphQLFieldDefinition>

    interface Builder {

        fun setDomainSelection(
            coordinates: FieldCoordinates,
            path: GQLOperationPath,
            graphQLFieldDefinition: GraphQLFieldDefinition
        ): Builder

        fun addArgument(path: GQLOperationPath, graphQLArgument: GraphQLArgument): Builder

        fun addAllArguments(arguments: Iterable<Pair<GQLOperationPath, GraphQLArgument>>): Builder

        fun addAllArguments(arguments: Map<GQLOperationPath, GraphQLArgument>): Builder

        fun addSelection(
            path: GQLOperationPath,
            graphQLFieldDefinition: GraphQLFieldDefinition
        ): Builder

        fun addAllSelections(
            selections: Iterable<Pair<GQLOperationPath, GraphQLFieldDefinition>>
        ): Builder

        fun addAllSelections(selections: Map<GQLOperationPath, GraphQLFieldDefinition>): Builder

        fun build(): DataElementCallable
    }
}
