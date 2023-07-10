package funcify.feature.datasource.rest.configuration

import org.springframework.context.annotation.Configuration

@Configuration
class RestApiDataElementSourceConfiguration {

    /* @ConditionalOnMissingBean(value = [RestApiServiceFactory::class])
    @Bean
    fun restApiServiceFactory(
        webClientCustomizerProvider: ObjectProvider<WebClientCustomizer>,
        codecCustomizerProvider: ObjectProvider<WebClientCodecCustomizer>
    ): RestApiServiceFactory {
        return DefaultRestApiServiceFactory(
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
                    |from any rest_api_services""".flatten()
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

    @Bean
    fun swaggerRestApiDataSourceSDLDefinitionImplementationStrategy(
        restApiDataSourceProvider: ObjectProvider<RestApiDataElementSource>,
        swaggerSourceIndexSDLTypeResolutionTemplateProvider:
            ObjectProvider<SwaggerSourceIndexSDLTypeResolutionStrategyTemplate>
    ): SchematicVertexSDLDefinitionImplementationStrategy {
        val typeResolutionStrategy: SwaggerSourceIndexSDLTypeResolutionStrategyTemplate =
            swaggerSourceIndexSDLTypeResolutionTemplateProvider.getIfAvailable {
                DefaultSwaggerSourceIndexSDLTypeResolutionStrategy()
            }
        val sdlDefinitionImplementationStrategies =
            restApiDataSourceProvider
                .stream()
                .filter { rds -> rds.sourceMetamodel is SwaggerRestApiSourceMetamodel }
                .map { rds ->
                    SwaggerRestApiDataSourceIndexBasedSDLDefinitionImplementationStrategy(
                        rds,
                        DefaultSwaggerSourceIndexSDLDefinitionFactory(typeResolutionStrategy)
                    )
                }
                .toPersistentList()
        return CompositeSwaggerSourceIndexSDLDefinitionImplementationStrategy(
            sdlDefinitionImplementationStrategies
        )
    }

    @ConditionalOnMissingBean(value = [SwaggerRestApiJsonRetrievalStrategyProvider::class])
    @Bean
    fun swaggerRestApiJsonRetrievalStrategyProvider(
        jsonMapper: JsonMapper,
        swaggerRestApiJsonResponsePostProcessingStrategyProvider:
            ObjectProvider<SwaggerRestApiJsonResponsePostProcessingStrategy>
    ): SwaggerRestApiJsonRetrievalStrategyProvider {
        return DefaultSwaggerRestApiJsonRetrievalStrategyProvider(
            jsonMapper = jsonMapper,
            postProcessingStrategy =
                swaggerRestApiJsonResponsePostProcessingStrategyProvider.getIfAvailable {
                    DefaultSwaggerRestApiJsonResponsePostProcessingStrategy()
                }
        )
    }*/
}
