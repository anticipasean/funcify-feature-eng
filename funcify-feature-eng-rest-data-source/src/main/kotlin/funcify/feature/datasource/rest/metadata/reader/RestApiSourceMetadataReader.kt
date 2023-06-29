package funcify.feature.datasource.rest.metadata.reader

import funcify.feature.datasource.rest.schema.RestApiSourceIndex
import funcify.feature.schema.dataelementsource.metadata.reader.DataSourceMetadataReader

interface RestApiSourceMetadataReader<MD> : DataSourceMetadataReader<MD, RestApiSourceIndex> {

}
