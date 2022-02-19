package funcify.feature.configuration

import funcify.feature.service.GraphQLServerRequestInterceptor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.server.WebFilter


/**
 * Configurations specific to the GraphQL setup for this service
 * @author smccarron
 * @created 2/1/22
 */
@Configuration
class GraphQLConfiguration {

    @Bean
    fun graphQlServerRequestInterceptor(graphQLServerRequestInterceptor: GraphQLServerRequestInterceptor): WebFilter {
        return graphQLServerRequestInterceptor;
    }

}