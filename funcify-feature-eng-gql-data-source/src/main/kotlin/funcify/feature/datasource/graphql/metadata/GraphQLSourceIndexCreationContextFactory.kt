package funcify.feature.datasource.graphql.metadata

import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.schema.datasource.DataSource
import graphql.schema.GraphQLObjectType

/**
 *
 * @author smccarron
 * @created 2022-07-05
 */
interface GraphQLSourceIndexCreationContextFactory {

    fun createRootSourceIndexCreationContextForQueryGraphQLObjectType(
        graphQLApiDataSourceKey: DataSource.Key<GraphQLSourceIndex>,
        graphQLObjectType: GraphQLObjectType
    ): GraphQLSourceIndexCreationContext<GraphQLObjectType>

}
