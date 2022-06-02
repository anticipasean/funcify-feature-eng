package funcify.feature.datasource.rest.factory

import funcify.feature.datasource.rest.RestApiDataSource
import funcify.feature.datasource.rest.RestApiService
import funcify.feature.datasource.rest.error.RestApiDataSourceException
import funcify.feature.datasource.rest.error.RestApiErrorResponse
import funcify.feature.datasource.rest.metadata.RestApiFetcherMetadataProvider
import funcify.feature.datasource.rest.reader.RestApiSourceMetadataReader
import funcify.feature.datasource.rest.schema.RestApiSourceIndex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.SourceMetamodel
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import org.slf4j.Logger

internal class DefaultRestApiDataSourceFactory<MD>(
    val restApiFetcherMetadataProvider: RestApiFetcherMetadataProvider<MD>,
    val restApiSourceMetadataReader: RestApiSourceMetadataReader<MD>
) : RestApiDataSourceFactory {

    companion object {
        private val logger: Logger = loggerFor<DefaultRestApiDataSourceFactory<*>>()

        internal data class DefaultRestApiDataSourceKey(
            override val name: String,
            override val sourceType: DataSourceType
        ) : DataSource.Key<RestApiSourceIndex> {}

        internal data class DefaultRestApiDataSource(
            override val name: String,
            override val restApiService: RestApiService,
            override val sourceMetamodel: SourceMetamodel<RestApiSourceIndex>
        ) : RestApiDataSource {
            override val key: DataSource.Key<RestApiSourceIndex> by lazy {
                DefaultRestApiDataSourceKey(name, sourceType)
            }
        }
    }

    override fun createRestApiDataSource(name: String, service: RestApiService): RestApiDataSource {
        logger.info(
            """create_rest_api_data_source: [ name: $name, 
                |service.service_context_path: ${service.serviceContextPath} ]
                |""".flattenIntoOneLine()
        )
        return restApiFetcherMetadataProvider
            .provideMetadata(service)
            .map { metadata: MD ->
                restApiSourceMetadataReader.readSourceMetamodelFromMetadata(metadata)
            }
            .map { sourceMetamodel: SourceMetamodel<RestApiSourceIndex> ->
                DefaultRestApiDataSource(
                    name = name,
                    sourceMetamodel = sourceMetamodel,
                    restApiService = service
                )
            }
            .blockForFirst()
            .orElseThrow { t: Throwable ->
                RestApiDataSourceException(
                    RestApiErrorResponse.UNEXPECTED_ERROR,
                    """error when retrieving or processing metadata 
                        |for rest_api_data_source [ name: $name ]""".flattenIntoOneLine(),
                    t
                )
            }
    }
}
