package funcify.feature.spring.configuration

import funcify.feature.spring.router.GraphQLWebFluxHandlerFunction
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.RequestPredicates
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerResponse

/**
 *
 * @author smccarron
 * @created 2/19/22
 */
@Configuration
open class SpringGraphQLWebFluxConfiguration {

    companion object {
        private const val MEDIA_TYPE_APPLICATION_GRAPHQL_VALUE = "application/graphql"
    }

    @Bean
    open fun graphQLWebFluxRouterFunction(
        @Value("\${feature-eng-service.graphql.path:/graphql}") graphQLPath: String,
        graphQLWebFluxHandlerFunction: GraphQLWebFluxHandlerFunction
    ): RouterFunction<ServerResponse> {
        return RouterFunctions.route()
            .POST(
                graphQLPath,
                RequestPredicates.accept(
                    MediaType.APPLICATION_JSON,
                    MediaType.valueOf(MEDIA_TYPE_APPLICATION_GRAPHQL_VALUE)
                ),
                graphQLWebFluxHandlerFunction::handle
            )
            .build()
    }
}
