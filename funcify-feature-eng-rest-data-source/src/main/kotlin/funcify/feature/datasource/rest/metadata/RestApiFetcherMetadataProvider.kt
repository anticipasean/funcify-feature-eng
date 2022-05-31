package funcify.feature.datasource.rest.metadata

import funcify.feature.datasource.rest.RestApiService
import funcify.feature.fetcher.metadata.DataFetcherMetadataProvider

interface RestApiFetcherMetadataProvider<MD> : DataFetcherMetadataProvider<RestApiService, MD> {

}
