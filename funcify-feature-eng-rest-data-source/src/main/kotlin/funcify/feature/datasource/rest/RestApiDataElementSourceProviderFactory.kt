package funcify.feature.datasource.rest

interface RestApiDataElementSourceProviderFactory {

    fun builder(): RestApiDataElementSourceProvider.Builder
}
