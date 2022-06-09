package funcify.feature.datasource.rest.metadata

import funcify.feature.datasource.rest.RestApiService
import funcify.feature.datasource.metadata.provider.DataSourceMetadataProvider

interface RestApiFetcherMetadataProvider<MD> : DataSourceMetadataProvider<RestApiService, MD> {

}
