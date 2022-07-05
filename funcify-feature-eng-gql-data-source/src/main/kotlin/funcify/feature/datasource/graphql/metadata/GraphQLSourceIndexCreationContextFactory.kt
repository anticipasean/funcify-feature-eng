package funcify.feature.datasource.graphql.metadata

import graphql.schema.GraphQLObjectType

/**
 *
 * @author smccarron
 * @created 2022-07-05
 */
interface GraphQLSourceIndexCreationContextFactory {

    fun createRootSourceIndexCreationContextForQueryGraphQLObjectType(
        graphQLObjectType: GraphQLObjectType
    ): GraphQLSourceIndexCreationContext<GraphQLObjectType>

}
