package funcify.feature.datasource.graphql.metadata.reader

import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.schema.datasource.DataElementSource
import graphql.schema.GraphQLObjectType

/**
 *
 * @author smccarron
 * @created 2022-07-05
 */
interface GraphQLSourceIndexCreationContextFactory {

    fun createRootSourceIndexCreationContextForQueryGraphQLObjectType(
        graphQLApiDataSourceKey: DataElementSource.Key<GraphQLSourceIndex>,
        graphQLObjectType: GraphQLObjectType
    ): GraphQLSourceIndexCreationContext<GraphQLObjectType>

}
