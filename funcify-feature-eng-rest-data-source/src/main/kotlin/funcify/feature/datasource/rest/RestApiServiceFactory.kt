package funcify.feature.datasource.rest

interface RestApiServiceFactory {

    fun builder(): RestApiService.Builder
}
