package funcify.feature.datasource.graphql.factory

import funcify.feature.datasource.graphql.GraphQLApiDataSource
import funcify.feature.datasource.graphql.GraphQLApiService

/**
 *
 * @author smccarron
 * @created 4/10/22
 */
interface GraphQLApiDataSourceFactory {

    fun createGraphQLApiDataSource(
        name: String,
        graphQLApiService: GraphQLApiService
    ): GraphQLApiDataSource
    
}
