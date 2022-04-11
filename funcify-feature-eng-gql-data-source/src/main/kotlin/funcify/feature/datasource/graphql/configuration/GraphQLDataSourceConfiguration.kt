package funcify.feature.datasource.graphql.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import funcify.feature.datasource.graphql.factory.DefaultGraphQLApiServiceFactory
import funcify.feature.datasource.graphql.factory.GraphQLApiServiceFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientCodecCustomizer
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer
import org.springframework.context.annotation.Configuration


/**
 *
 * @author smccarron
 * @created 4/11/22
 */
@Configuration
class GraphQLDataSourceConfiguration {

    @ConditionalOnMissingBean(value = [GraphQLApiServiceFactory::class])
    @ConditionalOnBean(value = [ObjectMapper::class])
    fun graphQLApiServiceFactory(objectMapper: ObjectMapper,
                                 webClientCustomizerProvider: ObjectProvider<WebClientCustomizer>,
                                 codecCustomizerProvider: ObjectProvider<WebClientCodecCustomizer>): GraphQLApiServiceFactory {
        return DefaultGraphQLApiServiceFactory(objectMapper = objectMapper,
                                               webClientCustomizerProvider = webClientCustomizerProvider,
                                               codecCustomizerProvider = codecCustomizerProvider)
    }

}