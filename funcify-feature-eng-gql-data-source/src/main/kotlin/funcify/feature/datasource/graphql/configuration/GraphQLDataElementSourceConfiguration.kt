package funcify.feature.datasource.graphql.configuration

import funcify.feature.datasource.graphql.GraphQLApiDataElementSourceProviderFactory
import funcify.feature.datasource.graphql.GraphQLApiServiceFactory
import funcify.feature.datasource.graphql.factory.DefaultGraphQLApiDataElementSourceProviderFactory
import funcify.feature.datasource.graphql.factory.DefaultGraphQLApiServiceFactory
import funcify.feature.datasource.graphql.metadata.filter.InternalQueryExcludingTypeDefinitionRegistryFilter
import funcify.feature.datasource.graphql.metadata.filter.TypeDefinitionRegistryFilter
import funcify.feature.datasource.graphql.metadata.filter.UnsupportedDirectivesTypeDefinitionRegistryFilter
import funcify.feature.directive.MaterializationDirectiveRegistry
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
        InternalQueryExcludingTypeDefinitionRegistryFilter {
        return InternalQueryExcludingTypeDefinitionRegistryFilter()
    }

    @Bean
    fun unsupportedDirectivesTypeDefinitionRegistryFilter(
        materializationDirectiveRegistryProvider: ObjectProvider<MaterializationDirectiveRegistry>
    ): UnsupportedDirectivesTypeDefinitionRegistryFilter {
        return UnsupportedDirectivesTypeDefinitionRegistryFilter(
            materializationDirectiveRegistry =
                materializationDirectiveRegistryProvider.getIfAvailable {
                    MaterializationDirectiveRegistry.standardRegistry()
                }
        )
    }

    @ConditionalOnMissingBean(value = [GraphQLApiDataElementSourceProviderFactory::class])
    @Bean
    fun graphQLApiDataElementSourceProviderFactory(
        jsonMapper: JsonMapper,
        typeDefinitionRegistryFilterProvider: ObjectProvider<TypeDefinitionRegistryFilter>
    ): GraphQLApiDataElementSourceProviderFactory {
        return DefaultGraphQLApiDataElementSourceProviderFactory(
            jsonMapper = jsonMapper,
            typeDefinitionRegistryFilters = typeDefinitionRegistryFilterProvider.toList()
        )
    }
}
