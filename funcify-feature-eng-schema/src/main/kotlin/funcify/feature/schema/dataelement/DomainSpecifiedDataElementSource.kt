package funcify.feature.schema.dataelement

import funcify.feature.schema.directive.temporal.LastUpdatedCoordinatesRegistry
import funcify.feature.schema.path.operation.GQLOperationPath
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import kotlinx.collections.immutable.ImmutableMap

/**
 * @author smccarron
 * @created 2023-08-16
 */
interface DomainSpecifiedDataElementSource {

    val domainFieldCoordinates: FieldCoordinates

    val domainPath: GQLOperationPath

    val domainFieldDefinition: GraphQLFieldDefinition

    val argumentsByPath: ImmutableMap<GQLOperationPath, GraphQLArgument>

    val argumentsByName: ImmutableMap<String, GraphQLArgument>

    val argumentPathsByName: ImmutableMap<String, GQLOperationPath>

    val argumentsWithDefaultValuesByName: ImmutableMap<String, GraphQLArgument>

    val argumentsWithoutDefaultValuesByName: ImmutableMap<String, GraphQLArgument>

    val argumentsWithoutDefaultValuesByPath: ImmutableMap<GQLOperationPath, GraphQLArgument>

    val dataElementSource: DataElementSource

    val lastUpdatedCoordinatesRegistry: LastUpdatedCoordinatesRegistry

    interface Builder {

        fun domainFieldCoordinates(domainFieldCoordinates: FieldCoordinates): Builder

        fun domainPath(domainPath: GQLOperationPath): Builder

        fun domainFieldDefinition(domainFieldDefinition: GraphQLFieldDefinition): Builder

        fun dataElementSource(dataElementSource: DataElementSource): Builder

        fun lastUpdatedCoordinatesRegistry(
            lastUpdatedCoordinatesRegistry: LastUpdatedCoordinatesRegistry
        ): Builder

        fun build(): DomainSpecifiedDataElementSource
    }
}
