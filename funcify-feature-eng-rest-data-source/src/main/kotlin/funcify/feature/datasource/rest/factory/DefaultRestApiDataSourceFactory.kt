package funcify.feature.datasource.rest.factory

import funcify.feature.datasource.rest.RestApiDataElementSource
import funcify.feature.datasource.rest.RestApiService
import funcify.feature.datasource.rest.error.RestApiDataSourceException
import funcify.feature.datasource.rest.error.RestApiErrorResponse
import funcify.feature.datasource.rest.metadata.provider.RestApiMetadataProvider
import funcify.feature.datasource.rest.metadata.reader.RestApiSourceMetadataReader
import funcify.feature.datasource.rest.schema.RestApiSourceIndex
import funcify.feature.schema.dataelement.DataElementSource
import funcify.feature.schema.SourceType
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.toTry
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
            override val sourceType: SourceType
        ) : DataElementSource.Key<RestApiSourceIndex> {
            override val sourceIndexType: KClass<RestApiSourceIndex> = RestApiSourceIndex::class
        }

        internal data class DefaultRestApiDataElementSource(
            override val name: String,
            override val restApiService: RestApiService,
            override val sourceMetamodel: SourceMetamodel<RestApiSourceIndex>,
            override val key: DataElementSource.Key<RestApiSourceIndex>
                                                           ) : RestApiDataElementSource
    }

    override fun createRestApiDataSource(name: String, service: RestApiService): RestApiDataElementSource {
        logger.info(
            """create_rest_api_data_source: [ name: $name, 
                |service.service_context_path: ${service.serviceContextPath} ]
                |""".flatten()
        )
        return restApiMetadataProvider
            .provideMetadata(service)
            .map { metadata: MD ->
                val dataSourceKey: DataElementSource.Key<RestApiSourceIndex> =
                    DefaultRestApiDataSourceKey(name, DataElementSourceType.REST_API)
                dataSourceKey to
                    restApiSourceMetadataReader.readSourceMetamodelFromMetadata(
                        dataSourceKey,
                        metadata
                    )
            }
            .map {
                sourceMetamodelPair:
                    Pair<DataElementSource.Key<RestApiSourceIndex>, SourceMetamodel<RestApiSourceIndex>> ->
                DefaultRestApiDataElementSource(
                    name = name,
                    sourceMetamodel = sourceMetamodelPair.second,
                    restApiService = service,
                    key = sourceMetamodelPair.first
                                               )
            }
            .toTry()
            .orElseThrow { t: Throwable ->
                RestApiDataSourceException(
                    RestApiErrorResponse.UNEXPECTED_ERROR,
                    """error when retrieving or processing metadata 
                        |for rest_api_data_source [ name: $name ]""".flatten(),
                    t
                )
            }
    }
}
