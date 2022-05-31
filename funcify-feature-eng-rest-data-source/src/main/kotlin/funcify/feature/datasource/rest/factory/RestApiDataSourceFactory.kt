package funcify.feature.datasource.rest.factory

import funcify.feature.datasource.rest.RestApiDataSource
import funcify.feature.datasource.rest.RestApiService

interface RestApiDataSourceFactory {

    fun createRestApiDataSource(name: String, service: RestApiService): RestApiDataSource

}
