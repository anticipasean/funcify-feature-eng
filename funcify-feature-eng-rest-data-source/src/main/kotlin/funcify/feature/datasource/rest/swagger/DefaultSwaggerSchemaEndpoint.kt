package funcify.feature.datasource.rest.swagger

import arrow.core.identity
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.jayway.jsonpath.JsonPath
import funcify.feature.tools.container.deferred.Deferred
import org.reactivestreams.Publisher
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer
import org.springframework.http.HttpMethod

/**
 *
 * @author smccarron
 * @created 2022-07-13
 */
class DefaultSwaggerSchemaEndpoint(
    override val name: String,
    override val httpMethod: HttpMethod = DEFAULT_HTTP_METHOD,
    private val webClientCustomizer: WebClientCustomizer = DEFAULT_WEB_CLIENT_CUSTOMIZER,
    val requestBodyCreator: () -> Publisher<JsonNode> = DEFAULT_REQUEST_BODY_CREATOR,
    override val responseOpenApiSpecificationJsonPath: JsonPath =
        DEFAULT_RESPONSE_OPEN_API_SPECIFICATION_JSON_PATH,
    val responseJsonNodePreprocessor: (JsonNode) -> JsonNode =
        DEFAULT_RESPONSE_JSON_NODE_PREPROCESSOR
) : SwaggerSchemaEndpoint {

    companion object {
        private val DEFAULT_HTTP_METHOD: HttpMethod = HttpMethod.POST
        private val DEFAULT_WEB_CLIENT_CUSTOMIZER: WebClientCustomizer = WebClientCustomizer {}
        private val DEFAULT_REQUEST_BODY_CREATOR: () -> Publisher<JsonNode> = { ->
            Deferred.completed<JsonNode>(JsonNodeFactory.instance.objectNode())
        }
        private val DEFAULT_RESPONSE_OPEN_API_SPECIFICATION_JSON_PATH: JsonPath =
            JsonPath.compile("$")
        private val DEFAULT_RESPONSE_JSON_NODE_PREPROCESSOR: (JsonNode) -> JsonNode = ::identity
    }

    override fun webClientCustomizer(): WebClientCustomizer {
        return webClientCustomizer
    }

    override fun requestBodyCreator(): () -> Publisher<JsonNode> {
        return requestBodyCreator
    }

    override fun responseOpenApiSpecificationJsonNodeContentPreprocessor(): (JsonNode) -> JsonNode {
        return responseJsonNodePreprocessor
    }
}
