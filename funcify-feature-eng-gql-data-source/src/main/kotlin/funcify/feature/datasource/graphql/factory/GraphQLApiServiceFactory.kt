package funcify.feature.datasource.graphql.factory

import funcify.feature.datasource.graphql.GraphQLApiService

/**
 *
 * @author smccarron
 * @created 4/11/22
 */
interface GraphQLApiServiceFactory {

    fun builder(): GraphQLApiService.Builder
}
