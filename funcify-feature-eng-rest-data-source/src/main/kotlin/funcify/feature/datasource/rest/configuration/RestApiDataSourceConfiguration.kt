package funcify.feature.datasource.rest.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import funcify.feature.datasource.rest.factory.DefaultRestApiServiceFactory
import funcify.feature.datasource.rest.factory.RestApiServiceFactory
import funcify.feature.datasource.rest.swagger.DefaultSwaggerSchemaEndpointRegistry
import funcify.feature.datasource.rest.swagger.SwaggerSchemaEndpointRegistry
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

    @ConditionalOnMissingBean(value = [SwaggerSchemaEndpointRegistry::class])
    fun swaggerSchemaEndpointRegistry(): SwaggerSchemaEndpointRegistry {
        return DefaultSwaggerSchemaEndpointRegistry()
    }

    //    @ConditionalOnMissingBean(value = [RestApiDataSourceFactory::class])
    //    @ConditionalOnBean(value = [ObjectMapper::class])
    //    @Bean
    //    fun RestApiDataSourceFactory(
    //        objectMapper: ObjectMapper,
    //        restFetcherMetadataProvider: RestApiFetcherMetadataProvider<*>,
    //        restMetadataReaderProvider: RestApiSourceMetadataReader<*>
    //    ): RestApiDataSourceFactory {
    //        return DefaultRestApiDataSourceFactory<Any>(
    //            restApiFetcherMetadataProvider = restFetcherMetadataProvider,
    //            restApiSourceMetadataReader = restMetadataReaderProvider
    //        )
    //    }

    //    @ConditionalOnMissingBean(value = [RestApiFetcherMetadataProvider::class])
    //    @ConditionalOnBean(value = [ObjectMapper::class])
    //    @Bean
    //    fun restFetcherMetadataProvider(objectMapper: ObjectMapper):
    // RestApiFetcherMetadataProvider<*> {
    //
    //    }

    //    @ConditionalOnMissingBean(value = [RestApiSourceMetadataReader::class])
    //    @Bean
    //    fun restApiSourceMetadataReader(
    //    ): RestApiSourceMetadataReader<*> {
    //
    //    }
}
