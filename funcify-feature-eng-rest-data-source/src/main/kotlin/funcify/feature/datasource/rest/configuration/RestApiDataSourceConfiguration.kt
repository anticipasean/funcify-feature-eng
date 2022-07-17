package funcify.feature.datasource.rest.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import funcify.feature.datasource.rest.factory.DefaultRestApiDataSourceFactory
import funcify.feature.datasource.rest.factory.DefaultRestApiServiceFactory
import funcify.feature.datasource.rest.factory.RestApiDataSourceFactory
import funcify.feature.datasource.rest.factory.RestApiServiceFactory
import funcify.feature.datasource.rest.metadata.provider.DefaultSwaggerRestApiMetadataProvider
import funcify.feature.datasource.rest.metadata.provider.SwaggerRestApiMetadataProvider
import funcify.feature.datasource.rest.metadata.reader.DefaultSwaggerRestApiSourceMetadataReader
import funcify.feature.datasource.rest.metadata.reader.SwaggerRestApiSourceMetadataReader
import funcify.feature.datasource.rest.swagger.SwaggerSchemaEndpointRegistry
import funcify.feature.json.JsonMapper
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
        swaggerSchemaEndpointRegistry: SwaggerSchemaEndpointRegistry
    ): SwaggerRestApiMetadataProvider {
        return DefaultSwaggerRestApiMetadataProvider(
            jsonMapper = jsonMapper,
            swaggerSchemaEndpointRegistry = swaggerSchemaEndpointRegistry
        )
    }

    @ConditionalOnMissingBean(value = [SwaggerRestApiSourceMetadataReader::class])
    @Bean
    fun swaggerRestApiSourceMetadataReader(): SwaggerRestApiSourceMetadataReader {
        return DefaultSwaggerRestApiSourceMetadataReader()
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
