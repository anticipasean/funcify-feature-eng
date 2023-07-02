package funcify.feature.datasource.rest.metadata.provider

import funcify.feature.datasource.rest.RestApiService
import funcify.feature.schema.dataelement.metadata.provider.DataSourceMetadataProvider

interface RestApiMetadataProvider<MD> : DataSourceMetadataProvider<RestApiService, MD> {

}
