package funcify.feature.datasource.graphql.metadata

import graphql.schema.GraphQLFieldDefinition

fun interface GraphQLApiSourceMetadataFilter {

    companion object {

        val INCLUDE_ALL_FILTER: GraphQLApiSourceMetadataFilter = GraphQLApiSourceMetadataFilter {
            true
        }
    }

    fun includeGraphQLFieldDefinition(graphQLFieldDefinition: GraphQLFieldDefinition): Boolean
}
