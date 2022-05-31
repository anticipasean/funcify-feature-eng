package funcify.feature.datasource.rest.factory

import funcify.feature.datasource.rest.RestApiService

interface RestApiServiceFactory {

    fun builder(): RestApiService.Builder

}
