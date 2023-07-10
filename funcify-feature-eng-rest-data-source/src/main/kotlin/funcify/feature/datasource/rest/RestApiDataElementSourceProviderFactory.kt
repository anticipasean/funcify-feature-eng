package funcify.feature.datasource.rest

import funcify.feature.datasource.rest.RestApiDataElementSourceProvider

interface RestApiDataElementSourceProviderFactory {

    fun builder(): RestApiDataElementSourceProvider.Builder
}
