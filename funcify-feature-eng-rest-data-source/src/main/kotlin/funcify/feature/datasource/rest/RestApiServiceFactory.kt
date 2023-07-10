package funcify.feature.datasource.rest

import funcify.feature.datasource.rest.RestApiService

interface RestApiServiceFactory {

    fun builder(): RestApiService.Builder

}
