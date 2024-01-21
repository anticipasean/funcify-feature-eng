package funcify.feature.datasource.graphql.configuration

import funcify.feature.datasource.graphql.GraphQLApiDataElementSourceProviderFactory
import funcify.feature.datasource.graphql.GraphQLApiServiceFactory
import funcify.feature.datasource.graphql.factory.DefaultGraphQLApiDataElementSourceProviderFactory
import funcify.feature.datasource.graphql.factory.DefaultGraphQLApiServiceFactory
import funcify.feature.datasource.graphql.metadata.transformer.InternalQueryExcludingTypeDefinitionRegistryTransformer
import funcify.feature.schema.sdl.transformer.TypeDefinitionRegistryTransformer
import funcify.feature.tools.json.JsonMapper
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientCodecCustomizer
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * @author smccarron
 * @created 4/11/22
 */
@Configuration
class GraphQLDataElementSourceConfiguration {

    @ConditionalOnMissingBean(value = [GraphQLApiServiceFactory::class])
    @Bean
    fun graphQLApiServiceFactory(
        jsonMapper: JsonMapper,
        webClientCustomizerProvider: ObjectProvider<WebClientCustomizer>,
        codecCustomizerProvider: ObjectProvider<WebClientCodecCustomizer>
    ): GraphQLApiServiceFactory {
        return DefaultGraphQLApiServiceFactory(
            jsonMapper = jsonMapper,
            webClientCustomizerProvider = webClientCustomizerProvider,
            codecCustomizerProvider = codecCustomizerProvider
        )
    }

    @Bean
    fun internalQueryExcludingTypeDefinitionRegistryFilter():
        InternalQueryExcludingTypeDefinitionRegistryTransformer {
        return InternalQueryExcludingTypeDefinitionRegistryTransformer()
    }

    @ConditionalOnMissingBean(value = [GraphQLApiDataElementSourceProviderFactory::class])
    @Bean
    fun graphQLApiDataElementSourceProviderFactory(
        jsonMapper: JsonMapper,
        typeDefinitionRegistryTransformerProvider: ObjectProvider<TypeDefinitionRegistryTransformer>
    ): GraphQLApiDataElementSourceProviderFactory {
        return DefaultGraphQLApiDataElementSourceProviderFactory(
            jsonMapper = jsonMapper,
            typeDefinitionRegistryTransformers = typeDefinitionRegistryTransformerProvider.toList()
        )
    }
}
