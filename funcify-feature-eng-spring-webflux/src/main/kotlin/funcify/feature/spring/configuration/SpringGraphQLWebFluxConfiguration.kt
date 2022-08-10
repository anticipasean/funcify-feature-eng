package funcify.feature.spring.configuration

import arrow.core.toOption
import funcify.feature.json.JsonMapper
import funcify.feature.materializer.request.GraphQLExecutionInputCustomizer
import funcify.feature.materializer.request.RawGraphQLRequestFactory
import funcify.feature.materializer.threadlocal.ThreadLocalContextOperation
import funcify.feature.materializer.threadlocal.ThreadLocalContextOperationFactory
import funcify.feature.spring.router.GraphQLWebFluxHandlerFunction
import funcify.feature.spring.service.GraphQLSingleRequestExecutor
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
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
class SpringGraphQLWebFluxConfiguration {

    companion object {
        private const val MEDIA_TYPE_APPLICATION_GRAPHQL_VALUE = "application/graphql"
    }

    @Bean
    fun securityContextThreadLocalContextOperation(): ThreadLocalContextOperation {
        return ThreadLocalContextOperationFactory.defaultFactory()
            .builder()
            .extractInParentContext { SecurityContextHolder.getContext().toOption() }
            .setInChildContext { securityContext ->
                SecurityContextHolder.setContext(securityContext)
            }
            .build()
    }

    @Bean
    fun graphQLWebFluxRouterFunction(
        @Value("\${feature-eng-service.graphql.path}") graphQLPath: String,
        jsonMapper: JsonMapper,
        graphQLSingleRequestExecutor: GraphQLSingleRequestExecutor,
        rawGraphQLRequestFactory: RawGraphQLRequestFactory,
        graphQLExecutionInputCustomizerProvider: ObjectProvider<GraphQLExecutionInputCustomizer>
    ): RouterFunction<ServerResponse> {
        return RouterFunctions.route()
            .POST(
                graphQLPath,
                RequestPredicates.accept(
                    MediaType.APPLICATION_JSON,
                    MediaType.valueOf(MEDIA_TYPE_APPLICATION_GRAPHQL_VALUE)
                ),
                GraphQLWebFluxHandlerFunction(
                    jsonMapper = jsonMapper,
                    graphQLSingleRequestExecutor = graphQLSingleRequestExecutor,
                    rawGraphQLRequestFactory = rawGraphQLRequestFactory,
                    graphQLExecutionInputCustomizers =
                        graphQLExecutionInputCustomizerProvider.toList()
                )
            )
            .build()
    }
}
