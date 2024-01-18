package funcify.feature.schema.dataelement

import funcify.feature.schema.directive.temporal.LastUpdatedCoordinatesRegistry
import funcify.feature.schema.path.operation.GQLOperationPath
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLSchema
import kotlinx.collections.immutable.ImmutableMap

/**
 * @author smccarron
 * @created 2023-08-16
 */
interface DomainSpecifiedDataElementSource {

    val domainFieldCoordinates: FieldCoordinates

    val domainPath: GQLOperationPath

    val domainFieldDefinition: GraphQLFieldDefinition

    val domainArgumentsByPath: ImmutableMap<GQLOperationPath, GraphQLArgument>

    val domainArgumentsByName: ImmutableMap<String, GraphQLArgument>

    val domainArgumentPathsByName: ImmutableMap<String, GQLOperationPath>

    val domainArgumentsWithDefaultValuesByPath: ImmutableMap<GQLOperationPath, GraphQLArgument>

    val domainArgumentsWithDefaultValuesByName: ImmutableMap<String, GraphQLArgument>

    val domainArgumentsWithoutDefaultValuesByPath: ImmutableMap<GQLOperationPath, GraphQLArgument>

    val domainArgumentsWithoutDefaultValuesByName: ImmutableMap<String, GraphQLArgument>

    val allArgumentsByPath: ImmutableMap<GQLOperationPath, GraphQLArgument>

    val allArgumentsWithDefaultValuesByPath: ImmutableMap<GQLOperationPath, GraphQLArgument>

    val allArgumentsWithoutDefaultValuesByPath: ImmutableMap<GQLOperationPath, GraphQLArgument>

    val dataElementSource: DataElementSource

    val graphQLSchema: GraphQLSchema

    val lastUpdatedCoordinatesRegistry: LastUpdatedCoordinatesRegistry

    fun findPathsForRequiredArgumentsForSelections(
        selections: Set<GQLOperationPath>
    ): Set<GQLOperationPath>

    interface Builder {

        fun domainFieldCoordinates(domainFieldCoordinates: FieldCoordinates): Builder

        fun domainPath(domainPath: GQLOperationPath): Builder

        fun domainFieldDefinition(domainFieldDefinition: GraphQLFieldDefinition): Builder

        fun putArgumentForPathWithinDomain(
            argumentPath: GQLOperationPath,
            graphQLArgument: GraphQLArgument
        ): Builder

        fun dataElementSource(dataElementSource: DataElementSource): Builder

        fun graphQLSchema(graphQLSchema: GraphQLSchema): Builder

        fun lastUpdatedCoordinatesRegistry(
            lastUpdatedCoordinatesRegistry: LastUpdatedCoordinatesRegistry
        ): Builder

        fun build(): DomainSpecifiedDataElementSource
    }
}
