package funcify.feature.datasource.rest.factory

import funcify.feature.datasource.rest.RestApiDataElementSource
import funcify.feature.datasource.rest.RestApiService

interface RestApiDataSourceFactory {

    fun createRestApiDataSource(name: String, service: RestApiService): RestApiDataElementSource

}
