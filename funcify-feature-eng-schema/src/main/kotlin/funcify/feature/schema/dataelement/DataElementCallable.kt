package funcify.feature.schema.dataelement

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.schema.path.operation.GQLOperationPath
import graphql.language.Field
import graphql.language.Value
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

    fun update(transformer: Builder.() -> Builder): DataElementCallable

    interface Builder {

        fun setDomainSelection(
            coordinates: FieldCoordinates,
            path: GQLOperationPath,
            graphQLFieldDefinition: GraphQLFieldDefinition
        ): Builder

        fun selectField(field: Field): Builder

        fun addSelection(path: GQLOperationPath): Builder

        fun addSelectionDirective(path: GQLOperationPath): Builder

        fun addSelectionDirectiveWithValue(path: GQLOperationPath, value: Value<*>): Builder

        fun addAllSelections(selections: Iterable<GQLOperationPath>): Builder

        fun build(): DataElementCallable
    }
}
