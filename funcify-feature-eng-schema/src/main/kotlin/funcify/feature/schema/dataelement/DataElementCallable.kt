package funcify.feature.schema.dataelement

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.path.operation.GQLOperationPath
import graphql.language.Field
import graphql.language.Value
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-08-01
 */
interface DataElementCallable : (ImmutableMap<GQLOperationPath, JsonNode>) -> Mono<JsonNode> {

    val domainFieldCoordinates: FieldCoordinates

    val domainPath: GQLOperationPath

    val domainGraphQLFieldDefinition: GraphQLFieldDefinition

    //val argumentsByPath: ImmutableMap<GQLOperationPath, GraphQLArgument>

    //val selectionsByPath: ImmutableMap<GQLOperationPath, GraphQLFieldDefinition>

    val selections: ImmutableSet<GQLOperationPath>

    override fun invoke(arguments: ImmutableMap<GQLOperationPath, JsonNode>): Mono<JsonNode>

    interface Builder {

        fun selectDomain(
            coordinates: FieldCoordinates,
            path: GQLOperationPath,
            graphQLFieldDefinition: GraphQLFieldDefinition
        ): Builder

        fun selectFieldWithinDomain(field: Field): Builder

        fun selectPathWithinDomain(path: GQLOperationPath): Builder

        fun selectDirectivePathWithValueWithinDomain(
            path: GQLOperationPath,
            value: Value<*>
        ): Builder

        fun selectAllPathsWithinDomain(selections: Iterable<GQLOperationPath>): Builder

        fun build(): DataElementCallable
    }
}
