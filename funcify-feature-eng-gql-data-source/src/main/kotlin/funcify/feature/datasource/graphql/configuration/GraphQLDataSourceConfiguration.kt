package funcify.feature.datasource.graphql.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import funcify.feature.datasource.graphql.factory.DefaultGraphQLApiDataSourceFactory
import funcify.feature.datasource.graphql.factory.DefaultGraphQLApiServiceFactory
import funcify.feature.datasource.graphql.factory.GraphQLApiDataSourceFactory
import funcify.feature.datasource.graphql.factory.GraphQLApiServiceFactory
import funcify.feature.datasource.graphql.metadata.filter.CompositeGraphQLApiSourceMetadataFilter
import funcify.feature.datasource.graphql.metadata.reader.ComprehensiveGraphQLApiSourceMetadataReader
import funcify.feature.datasource.graphql.metadata.provider.DefaultGraphQLApiSourceMetadataProvider
import funcify.feature.datasource.graphql.metadata.reader.DefaultGraphQLSourceIndexCreationContextFactory
import funcify.feature.datasource.graphql.metadata.filter.GraphQLApiSourceMetadataFilter
import funcify.feature.datasource.graphql.metadata.provider.GraphQLApiSourceMetadataProvider
import funcify.feature.datasource.graphql.metadata.reader.GraphQLApiSourceMetadataReader
import funcify.feature.datasource.graphql.metadata.reader.GraphQLSourceIndexCreationContextFactory
import funcify.feature.datasource.graphql.schema.DefaultGraphQLSourceIndexFactory
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndexFactory
import funcify.feature.datasource.graphql.sdl.GraphQLSourceIndexBasedSDLDefinitionImplementationStrategy
import funcify.feature.datasource.sdl.SchematicVertexSDLDefinitionImplementationStrategy
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientCodecCustomizer
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 *
 * @author smccarron
 * @created 4/11/22
 */
@Configuration
class GraphQLDataSourceConfiguration {

    @ConditionalOnMissingBean(value = [GraphQLApiServiceFactory::class])
    @Bean
    fun graphQLApiServiceFactory(
        objectMapper: ObjectMapper,
        webClientCustomizerProvider: ObjectProvider<WebClientCustomizer>,
        codecCustomizerProvider: ObjectProvider<WebClientCodecCustomizer>
    ): GraphQLApiServiceFactory {
        return DefaultGraphQLApiServiceFactory(
            objectMapper = objectMapper,
            webClientCustomizerProvider = webClientCustomizerProvider,
            codecCustomizerProvider = codecCustomizerProvider
        )
    }

    @ConditionalOnMissingBean(value = [GraphQLApiDataSourceFactory::class])
    @Bean
    fun graphQLApiDataSourceFactory(
        objectMapper: ObjectMapper,
        graphQLApiSourceMetadataProvider: ObjectProvider<GraphQLApiSourceMetadataProvider>,
        graphQLMetadataReaderProvider: ObjectProvider<GraphQLApiSourceMetadataReader>,
        graphQLSourceIndexCreationContextFactoryProvider:
            ObjectProvider<GraphQLSourceIndexCreationContextFactory>,
        graphQLSourceIndexFactoryProvider: ObjectProvider<GraphQLSourceIndexFactory>,
        graphQLApiSourceMetadataFilter: CompositeGraphQLApiSourceMetadataFilter
    ): GraphQLApiDataSourceFactory {
        return DefaultGraphQLApiDataSourceFactory(
            graphQLApiSourceMetadataProvider =
                graphQLApiSourceMetadataProvider.getIfAvailable {
                    DefaultGraphQLApiSourceMetadataProvider(objectMapper = objectMapper)
                },
            graphQLApiSourceMetadataReader =
                graphQLMetadataReaderProvider.getIfAvailable {
                    ComprehensiveGraphQLApiSourceMetadataReader(
                        graphQLSourceIndexFactoryProvider.getIfAvailable {
                            DefaultGraphQLSourceIndexFactory()
                        },
                        graphQLSourceIndexCreationContextFactoryProvider.getIfAvailable {
                            DefaultGraphQLSourceIndexCreationContextFactory
                        },
                        graphQLApiSourceMetadataFilter
                    )
                }
        )
    }

    @ConditionalOnMissingBean(value = [GraphQLSourceIndexFactory::class])
    @Bean
    fun graphQLSourceIndexFactory(): GraphQLSourceIndexFactory {
        return DefaultGraphQLSourceIndexFactory()
    }

    @ConditionalOnMissingBean(value = [GraphQLApiSourceMetadataProvider::class])
    @Bean
    fun graphQLFetcherMetadataProvider(
        objectMapper: ObjectMapper
    ): GraphQLApiSourceMetadataProvider {
        return DefaultGraphQLApiSourceMetadataProvider(objectMapper = objectMapper)
    }

    @ConditionalOnMissingBean(value = [CompositeGraphQLApiSourceMetadataFilter::class])
    @Bean
    fun compositeGraphQLApiSourceMetadataFilter(
        filters: ObjectProvider<GraphQLApiSourceMetadataFilter>
    ): CompositeGraphQLApiSourceMetadataFilter {
        return CompositeGraphQLApiSourceMetadataFilter(filtersProvider = filters)
    }

    @ConditionalOnMissingBean(value = [GraphQLApiSourceMetadataReader::class])
    @ConditionalOnBean(value = [GraphQLSourceIndexFactory::class])
    @Bean
    fun graphQLApiSourceMetadataReader(
        graphQLSourceIndexFactory: GraphQLSourceIndexFactory,
        compositeGraphQLApiSourceMetadataFilter: CompositeGraphQLApiSourceMetadataFilter
    ): GraphQLApiSourceMetadataReader {
        return ComprehensiveGraphQLApiSourceMetadataReader(
            graphQLSourceIndexFactory = graphQLSourceIndexFactory,
            graphQLSourceIndexCreationContextFactory =
                DefaultGraphQLSourceIndexCreationContextFactory,
            graphQLApiSourceMetadataFilter = compositeGraphQLApiSourceMetadataFilter
        )
    }

    @Bean
    fun graphQLSourceIndexBasedSDLDefinitionImplementationStrategy():
        SchematicVertexSDLDefinitionImplementationStrategy {
        return GraphQLSourceIndexBasedSDLDefinitionImplementationStrategy()
    }
}
