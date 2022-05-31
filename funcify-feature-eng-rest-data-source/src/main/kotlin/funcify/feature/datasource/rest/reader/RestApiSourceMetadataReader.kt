package funcify.feature.datasource.rest.reader

import funcify.feature.datasource.rest.schema.RestApiSourceIndex
import funcify.feature.schema.datasource.reader.MetadataReader

interface RestApiSourceMetadataReader<MD> : MetadataReader<MD, RestApiSourceIndex> {

}
