package funcify.feature.datasource.graphql.reader

import graphql.schema.GraphQLFieldDefinition

interface GraphQLApiSourceMetadataFilter {

    fun includeGraphQLFieldDefinition(graphQLFieldDefinition: GraphQLFieldDefinition): Boolean

}
