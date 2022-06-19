package funcify.feature.datasource.graphql.schema

import funcify.feature.schema.datasource.SourceAttribute
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLOutputType
import kotlinx.collections.immutable.ImmutableList

interface GraphQLSourceAttribute : GraphQLSourceIndex, SourceAttribute<GraphQLSourceIndex> {

    val schemaFieldDefinition: GraphQLFieldDefinition

    override val dataType: GraphQLOutputType
        get() = schemaFieldDefinition.type

    val arguments: ImmutableList<GraphQLArgument>
}
