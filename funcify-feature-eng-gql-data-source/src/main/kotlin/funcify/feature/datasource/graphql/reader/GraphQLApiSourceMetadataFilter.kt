package funcify.feature.datasource.graphql.reader

import graphql.schema.GraphQLFieldDefinition

interface GraphQLApiSourceMetadataFilter {

    companion object {

        val INCLUDE_ALL_FILTER: GraphQLApiSourceMetadataFilter by lazy {
            object : GraphQLApiSourceMetadataFilter {
                override fun includeGraphQLFieldDefinition(
                    graphQLFieldDefinition: GraphQLFieldDefinition
                ): Boolean {
                    return true
                }
            }
        }
    }

    fun includeGraphQLFieldDefinition(graphQLFieldDefinition: GraphQLFieldDefinition): Boolean
}
