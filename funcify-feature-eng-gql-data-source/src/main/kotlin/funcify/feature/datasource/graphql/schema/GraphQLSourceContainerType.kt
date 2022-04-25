package funcify.feature.datasource.graphql.schema

import funcify.feature.schema.datasource.SourceContainerType
import graphql.schema.GraphQLFieldsContainer

interface GraphQLSourceContainerType :
    GraphQLSourceIndex, SourceContainerType<GraphQLSourceAttribute> {

    val containerType: GraphQLFieldsContainer

}
