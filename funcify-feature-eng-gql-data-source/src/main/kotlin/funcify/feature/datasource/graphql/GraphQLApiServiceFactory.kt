package funcify.feature.datasource.graphql

/**
 * @author smccarron
 * @created 4/11/22
 */
fun interface GraphQLApiServiceFactory {

    fun builder(): GraphQLApiService.Builder
}
