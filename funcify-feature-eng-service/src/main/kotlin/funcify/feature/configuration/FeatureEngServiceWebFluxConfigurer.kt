package funcify.feature.configuration

import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import org.slf4j.Logger
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.graphql.GraphQlCorsProperties
import org.springframework.boot.autoconfigure.graphql.GraphQlProperties
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.reactive.config.CorsRegistry
import org.springframework.web.reactive.config.EnableWebFlux
import org.springframework.web.reactive.config.WebFluxConfigurationSupport
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.support.RouterFunctionMapping

/**
 * @author smccarron
 * @created 2023-06-28
 */
@Configuration
@EnableWebFlux
class FeatureEngServiceWebFluxConfigurer(
    private val graphQlProperties: GraphQlProperties,
    private val graphQlCorsProperties: GraphQlCorsProperties,
    private val routerFunctionProvider: ObjectProvider<RouterFunction<ServerResponse>>,
) : WebFluxConfigurationSupport() {

    companion object {
        private val logger: Logger = loggerFor<FeatureEngServiceWebFluxConfigurer>()
    }

    override fun createRouterFunctionMapping(): RouterFunctionMapping {
        val routerFunctions: List<RouterFunction<ServerResponse>> = routerFunctionProvider.toList()
        val methodTag: String = "create_routing_function_mapping"
        logger.info("${methodTag}: [ routes_provided.size: ${routerFunctions.size} ]")
        if (routerFunctions.isEmpty()) {
            logger.warn(
                "${methodTag}: [ status: failed ] expected at least one router_function bean to be declared"
            )
        }
        return RouterFunctionMapping(
            routerFunctions.fold(RouterFunctions.route()) { rb, rf -> rb.add(rf) }.build()
        )
    }

    override fun addCorsMappings(registry: CorsRegistry) {
        if (graphQlProperties.graphiql.isEnabled) {
            val configuration: CorsConfiguration? = graphQlCorsProperties.toCorsConfiguration()
            if (configuration != null) {
                registry.addMapping(this.graphQlProperties.path).combine(configuration)
            }
        }
    }
}
