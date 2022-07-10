package funcify.feature.datasource.rest.metadata.reader

import funcify.feature.datasource.rest.schema.RestApiSourceIndex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceMetamodel
import io.swagger.v3.oas.models.OpenAPI

/**
 *
 * @author smccarron
 * @created 2022-07-10
 */
class DefaultSwaggerRestApiSourceMetadataReader : SwaggerRestApiSourceMetadataReader {

    override fun readSourceMetamodelFromMetadata(
        dataSourceKey: DataSource.Key<RestApiSourceIndex>,
        input: OpenAPI,
    ): SourceMetamodel<RestApiSourceIndex> {
        TODO("Not yet implemented")
    }

}
