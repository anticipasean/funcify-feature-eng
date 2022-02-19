package funcify.feature.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono


/**
 * Intercepts GraphQL Requests
 * @author smccarron
 * @created 1/31/22
 */
@Component
class GraphQLServerRequestInterceptor(@Value("\${dgs.graphql.path:/graphql}") val graphQLPath: String) : WebFilter {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(GraphQLServerRequestInterceptor::class.java);
    }

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        return if (exchange.request.method == HttpMethod.POST && exchange.request.path.contextPath().value().endsWith(graphQLPath)) {
            logger.info("filter: [ request.method: {}, request.path.context_path: {} ]", exchange.request.method, exchange.request.path.contextPath())
            return chain.filter(exchange)
        } else chain.filter(exchange)
    }

}