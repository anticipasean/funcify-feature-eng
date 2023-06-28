package funcify.feature.spring.router

import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import java.net.URI
import org.slf4j.Logger
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.HandlerFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.util.UriBuilder
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-06-28
 */
class GraphiQLWebFluxHandlerFunction(
    private val graphQLPath: String,
    private val graphiqlHtmlResource: ClassPathResource
) : HandlerFunction<ServerResponse> {

    companion object {
        private val logger: Logger = loggerFor<GraphiQLWebFluxHandlerFunction>()
    }

    override fun handle(request: ServerRequest): Mono<ServerResponse> {
        logger.info("handle: [ request.path: {} ]", request.path())
        return if (request.queryParam("path").isPresent) {
            ServerResponse.ok().contentType(MediaType.TEXT_HTML).bodyValue(graphiqlHtmlResource)
        } else {
            ServerResponse.temporaryRedirect(getRedirectUrl(request)).build()
        }
    }

    private fun getRedirectUrl(request: ServerRequest): URI {
        val builder: UriBuilder = request.uriBuilder()
        val pathQueryParam: String = applyContextPath(request, graphQLPath)
        builder.queryParam("path", pathQueryParam)
        return builder.build(request.pathVariables())
    }

    private fun applyContextPath(request: ServerRequest, path: String): String {
        val contextPath: String = request.requestPath().contextPath().toString()
        return if (contextPath.isNotBlank()) {
            contextPath + path
        } else {
            path
        }
    }
}
