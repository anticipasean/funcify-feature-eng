package funcify.feature.spring.configuration

import funcify.feature.materializer.request.GraphQLExecutionInputCustomizer
import funcify.feature.materializer.request.RawGraphQLRequestFactory
import funcify.feature.materializer.session.GraphQLSingleRequestSessionCoordinator
import funcify.feature.materializer.session.GraphQLSingleRequestSessionFactory
import funcify.feature.spring.configuration.SpringGraphQLWebFluxConfiguration.Companion.GraphiQlResourceHints
import funcify.feature.spring.router.GraphQLWebFluxHandlerFunction
import funcify.feature.spring.router.GraphiQLWebFluxHandlerFunction
import funcify.feature.spring.service.GraphQLSingleRequestExecutor
import funcify.feature.spring.service.SpringGraphQLSingleRequestExecutor
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.json.JsonMapper
import org.slf4j.Logger
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type
import org.springframework.boot.autoconfigure.graphql.GraphQlCorsProperties
import org.springframework.boot.autoconfigure.graphql.GraphQlProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ImportRuntimeHints
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.RequestPredicates
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerResponse

/**
 * @author smccarron
 * @created 2/19/22
 */
@ConditionalOnWebApplication(type = Type.REACTIVE)
@EnableConfigurationProperties(value = [GraphQlProperties::class, GraphQlCorsProperties::class])
@ImportRuntimeHints(value = [GraphiQlResourceHints::class])
@Configuration
class SpringGraphQLWebFluxConfiguration {

    companion object {
        private const val GRAPHIQL_INDEX_HTML_PATH = "graphiql/index.html"
        private val logger: Logger = loggerFor<SpringGraphQLWebFluxConfiguration>()
        private val GRAPHIQL_INDEX_HTML_RESOURCE: ClassPathResource =
            ClassPathResource(GRAPHIQL_INDEX_HTML_PATH)

        internal class GraphiQlResourceHints : RuntimeHintsRegistrar {
            override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
                hints.resources().registerPattern(GRAPHIQL_INDEX_HTML_PATH)
            }
        }
    }

    @Bean
    fun springGraphQLSingleRequestExecutor(
        graphQLSingleRequestSessionFactory: GraphQLSingleRequestSessionFactory,
        graphQLSingleRequestSessionCoordinator: GraphQLSingleRequestSessionCoordinator,
    ): GraphQLSingleRequestExecutor {
        return SpringGraphQLSingleRequestExecutor(
            graphQLSingleRequestSessionFactory = graphQLSingleRequestSessionFactory,
            graphQLSingleRequestSessionCoordinator = graphQLSingleRequestSessionCoordinator
        )
    }

    @Bean
    fun graphQLWebFluxRouterFunction(
        graphQlProperties: GraphQlProperties,
        jsonMapper: JsonMapper,
        graphQLSingleRequestExecutor: GraphQLSingleRequestExecutor,
        rawGraphQLRequestFactory: RawGraphQLRequestFactory,
        graphQLExecutionInputCustomizerProvider: ObjectProvider<GraphQLExecutionInputCustomizer>,
    ): RouterFunction<ServerResponse> {
        return RouterFunctions.route()
            .POST(
                graphQlProperties.path,
                RequestPredicates.accept(
                    MediaType.APPLICATION_JSON,
                    MediaType.APPLICATION_GRAPHQL,
                    MediaType.APPLICATION_GRAPHQL_RESPONSE
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

    @ConditionalOnExpression("\${spring.graphql.graphiql.enabled} == true")
    @Bean
    fun graphiQLWebFluxRouterFunction(
        graphQlProperties: GraphQlProperties
    ): RouterFunction<ServerResponse> {
        logger.info("enabling graphiql")
        return RouterFunctions.route()
            .GET(
                graphQlProperties.graphiql.path,
                GraphiQLWebFluxHandlerFunction(
                    graphQLPath = graphQlProperties.path,
                    graphiqlHtmlResource = GRAPHIQL_INDEX_HTML_RESOURCE
                )
            )
            .build()
    }
}
