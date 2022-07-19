package funcify.feature.datasource.rest.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import funcify.feature.datasource.rest.error.RestApiDataSourceException
import funcify.feature.datasource.rest.error.RestApiErrorResponse
import funcify.feature.datasource.rest.factory.DefaultRestApiDataSourceFactory
import funcify.feature.datasource.rest.factory.DefaultRestApiServiceFactory
import funcify.feature.datasource.rest.factory.RestApiDataSourceFactory
import funcify.feature.datasource.rest.factory.RestApiServiceFactory
import funcify.feature.datasource.rest.metadata.filter.CompositeSwaggerRestApiSourceMetadataFilter
import funcify.feature.datasource.rest.metadata.filter.SwaggerRestApiSourceMetadataFilter
import funcify.feature.datasource.rest.metadata.provider.DefaultSwaggerRestApiMetadataProvider
import funcify.feature.datasource.rest.metadata.provider.SwaggerRestApiMetadataProvider
import funcify.feature.datasource.rest.metadata.reader.DefaultSwaggerRestApiSourceMetadataReader
import funcify.feature.datasource.rest.metadata.reader.SwaggerRestApiSourceMetadataReader
import funcify.feature.datasource.rest.swagger.SwaggerSchemaEndpointRegistry
import funcify.feature.json.JsonMapper
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import io.swagger.v3.oas.models.OpenAPI
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientCodecCustomizer
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RestApiDataSourceConfiguration {

    @ConditionalOnMissingBean(value = [RestApiServiceFactory::class])
    @Bean
    fun restApiServiceFactory(
        objectMapper: ObjectMapper,
        webClientCustomizerProvider: ObjectProvider<WebClientCustomizer>,
        codecCustomizerProvider: ObjectProvider<WebClientCodecCustomizer>
    ): RestApiServiceFactory {
        return DefaultRestApiServiceFactory(
            objectMapper = objectMapper,
            webClientCustomizerProvider = webClientCustomizerProvider,
            codecCustomizerProvider = codecCustomizerProvider
        )
    }

    @ConditionalOnMissingBean(value = [SwaggerRestApiMetadataProvider::class])
    @Bean
    fun swaggerRestApiMetadataProvider(
        jsonMapper: JsonMapper,
        swaggerSchemaEndpointRegistryProvider: ObjectProvider<SwaggerSchemaEndpointRegistry>
    ): SwaggerRestApiMetadataProvider {
        val swaggerSchemaEndpointRegistry =
            swaggerSchemaEndpointRegistryProvider.getIfAvailable {
                throw RestApiDataSourceException(
                    RestApiErrorResponse.UNEXPECTED_ERROR,
                    """no swagger_schema_endpoint_registry bean has been 
                    |declared so no swagger_metadata can be provided 
                    |from any rest_api_services""".flattenIntoOneLine()
                )
            }
        return DefaultSwaggerRestApiMetadataProvider(
            jsonMapper = jsonMapper,
            swaggerSchemaEndpointRegistry = swaggerSchemaEndpointRegistry
        )
    }

    @ConditionalOnMissingBean(value = [SwaggerRestApiSourceMetadataReader::class])
    @Bean
    fun swaggerRestApiSourceMetadataReader(
        swaggerRestApiSourceMetadataFilterProvider:
            ObjectProvider<SwaggerRestApiSourceMetadataFilter>
    ): SwaggerRestApiSourceMetadataReader {
        val swaggerRestApiSourceMetadataFilters =
            swaggerRestApiSourceMetadataFilterProvider.toList()
        val compositeFilter: SwaggerRestApiSourceMetadataFilter =
            when {
                swaggerRestApiSourceMetadataFilters.isEmpty() -> {
                    SwaggerRestApiSourceMetadataFilter.INCLUDE_ALL_FILTER
                }
                else -> {
                    CompositeSwaggerRestApiSourceMetadataFilter(swaggerRestApiSourceMetadataFilters)
                }
            }
        return DefaultSwaggerRestApiSourceMetadataReader(
            swaggerRestApiSourceMetadataFilter = compositeFilter
        )
    }

    @ConditionalOnMissingBean(value = [RestApiDataSourceFactory::class])
    @Bean
    fun swaggerRestApiDataSourceFactory(
        swaggerRestApiMetadataProvider: SwaggerRestApiMetadataProvider,
        swaggerRestApiSourceMetadataReader: SwaggerRestApiSourceMetadataReader
    ): RestApiDataSourceFactory {
        return DefaultRestApiDataSourceFactory<OpenAPI>(
            swaggerRestApiMetadataProvider,
            swaggerRestApiSourceMetadataReader
        )
    }
}
