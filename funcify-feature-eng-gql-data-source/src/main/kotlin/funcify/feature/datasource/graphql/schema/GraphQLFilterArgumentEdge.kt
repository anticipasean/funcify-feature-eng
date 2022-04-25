package funcify.feature.datasource.graphql.schema

import funcify.feature.schema.edge.FilterEdge
import graphql.schema.GraphQLArgument

interface GraphQLFilterArgumentEdge : FilterEdge {

    val argument: GraphQLArgument


}
