package funcify.feature.datasource.rest.factory

import funcify.feature.datasource.rest.RestApiDataSource
import funcify.feature.datasource.rest.RestApiService
import funcify.feature.datasource.rest.error.RestApiDataSourceException
import funcify.feature.datasource.rest.error.RestApiErrorResponse
import funcify.feature.datasource.rest.metadata.provider.RestApiMetadataProvider
import funcify.feature.datasource.rest.metadata.reader.RestApiSourceMetadataReader
import funcify.feature.datasource.rest.schema.RestApiSourceIndex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.RawDataSourceType
import funcify.feature.schema.datasource.SourceMetamodel
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import kotlin.reflect.KClass
import org.slf4j.Logger

internal class DefaultRestApiDataSourceFactory<MD>(
    private val restApiMetadataProvider: RestApiMetadataProvider<MD>,
    private val restApiSourceMetadataReader: RestApiSourceMetadataReader<MD>
) : RestApiDataSourceFactory {

    companion object {
        private val logger: Logger = loggerFor<DefaultRestApiDataSourceFactory<*>>()

        internal data class DefaultRestApiDataSourceKey(
            override val name: String,
            override val dataSourceType: DataSourceType
        ) : DataSource.Key<RestApiSourceIndex> {
            override val sourceIndexType: KClass<RestApiSourceIndex> = RestApiSourceIndex::class
        }

        internal data class DefaultRestApiDataSource(
            override val name: String,
            override val restApiService: RestApiService,
            override val sourceMetamodel: SourceMetamodel<RestApiSourceIndex>,
            override val key: DataSource.Key<RestApiSourceIndex>
        ) : RestApiDataSource
    }

    override fun createRestApiDataSource(name: String, service: RestApiService): RestApiDataSource {
        logger.info(
            """create_rest_api_data_source: [ name: $name, 
                |service.service_context_path: ${service.serviceContextPath} ]
                |""".flatten()
        )
        return restApiMetadataProvider
            .provideMetadata(service)
            .map { metadata: MD ->
                val dataSourceKey: DataSource.Key<RestApiSourceIndex> =
                    DefaultRestApiDataSourceKey(name, RawDataSourceType.REST_API)
                dataSourceKey to
                    restApiSourceMetadataReader.readSourceMetamodelFromMetadata(
                        dataSourceKey,
                        metadata
                    )
            }
            .map {
                sourceMetamodelPair:
                    Pair<DataSource.Key<RestApiSourceIndex>, SourceMetamodel<RestApiSourceIndex>> ->
                DefaultRestApiDataSource(
                    name = name,
                    sourceMetamodel = sourceMetamodelPair.second,
                    restApiService = service,
                    key = sourceMetamodelPair.first
                )
            }
            .getOrElseThrow { t: Throwable ->
                RestApiDataSourceException(
                    RestApiErrorResponse.UNEXPECTED_ERROR,
                    """error when retrieving or processing metadata 
                        |for rest_api_data_source [ name: $name ]""".flatten(),
                    t
                )
            }
    }
}
