package funcify.feature.datasource.graphql

/**
 * @author smccarron
 * @created 4/10/22
 */
fun interface GraphQLApiDataElementSourceProviderFactory {

    fun builder(): GraphQLApiDataElementSourceProvider.Builder
}
