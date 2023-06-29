package funcify.feature.datasource.graphql.factory

import funcify.feature.datasource.graphql.GraphQLApiDataElementSourceProvider

/**
 * @author smccarron
 * @created 4/10/22
 */
fun interface GraphQLApiDataElementSourceProviderFactory {

    fun builder(): GraphQLApiDataElementSourceProvider.Builder
}
