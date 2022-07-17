package funcify.feature.datasource.rest.swagger

import arrow.core.identity
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.jayway.jsonpath.JsonPath
import funcify.feature.tools.container.deferred.Deferred
import java.net.URI
import org.reactivestreams.Publisher
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer
import org.springframework.http.HttpMethod
import org.springframework.web.util.UriBuilder

/**
 *
 * @author smccarron
 * @created 2022-07-13
 */
internal class DefaultSwaggerSchemaEndpoint(
    override val name: String,
    private val webClientCustomizer: WebClientCustomizer,
    override val httpMethod: HttpMethod,
    private val uriCustomizer: (UriBuilder) -> URI,
    private val requestBodyCreator: () -> Publisher<JsonNode>,
    override val responseOpenApiSpecificationJsonPath: JsonPath,
    private val responseJsonNodePreprocessor: (JsonNode) -> JsonNode
) : SwaggerSchemaEndpoint {

    companion object {
        private val DEFAULT_WEB_CLIENT_CUSTOMIZER: WebClientCustomizer = WebClientCustomizer {}
        private val DEFAULT_HTTP_METHOD: HttpMethod = HttpMethod.POST
        private val DEFAULT_URI_CUSTOMIZER: (UriBuilder) -> URI = { ub -> ub.build() }
        private val DEFAULT_REQUEST_BODY_CREATOR: () -> Publisher<JsonNode> = { ->
            Deferred.completed<JsonNode>(JsonNodeFactory.instance.objectNode())
        }
        private val DEFAULT_RESPONSE_OPEN_API_SPECIFICATION_JSON_PATH: JsonPath =
            JsonPath.compile("$")
        private val DEFAULT_RESPONSE_JSON_NODE_PREPROCESSOR: (JsonNode) -> JsonNode = ::identity

        internal class DefaultBuilder(
            private var name: String? = null,
            private var webClientCustomizer: WebClientCustomizer = DEFAULT_WEB_CLIENT_CUSTOMIZER,
            private var httpMethod: HttpMethod = DEFAULT_HTTP_METHOD,
            private var uriCustomizer: (UriBuilder) -> URI = DEFAULT_URI_CUSTOMIZER,
            private var requestBodyCreator: () -> Publisher<JsonNode> =
                DEFAULT_REQUEST_BODY_CREATOR,
            private var responseOpenApiSpecificationJsonPath: JsonPath =
                DEFAULT_RESPONSE_OPEN_API_SPECIFICATION_JSON_PATH,
            private var responseJsonNodePreprocessor: (JsonNode) -> JsonNode =
                DEFAULT_RESPONSE_JSON_NODE_PREPROCESSOR
        ) : SwaggerSchemaEndpoint.Builder {

            override fun name(name: String): SwaggerSchemaEndpoint.Builder {
                this.name = name
                return this
            }

            override fun webClientCustomizer(
                webClientCustomizer: WebClientCustomizer
            ): SwaggerSchemaEndpoint.Builder {
                this.webClientCustomizer = webClientCustomizer
                return this
            }

            override fun httpMethod(httpMethod: HttpMethod): SwaggerSchemaEndpoint.Builder {
                this.httpMethod = httpMethod
                return this
            }

            override fun uriCustomizer(
                uriCustomizer: (UriBuilder) -> URI
            ): SwaggerSchemaEndpoint.Builder {
                this.uriCustomizer = uriCustomizer
                return this
            }

            override fun requestBodyCreator(
                requestBodyCreator: () -> Publisher<JsonNode>
            ): SwaggerSchemaEndpoint.Builder {
                this.requestBodyCreator = requestBodyCreator
                return this
            }

            override fun responseOpenApiSpecificationJsonPath(
                responseOpenApiSpecificationJsonPath: JsonPath
            ): SwaggerSchemaEndpoint.Builder {
                this.responseOpenApiSpecificationJsonPath = responseOpenApiSpecificationJsonPath
                return this
            }

            override fun responseJsonNodePreprocessor(
                responseJsonNodePreprocessor: (JsonNode) -> JsonNode
            ): SwaggerSchemaEndpoint.Builder {
                this.responseJsonNodePreprocessor = responseJsonNodePreprocessor
                return this
            }

            override fun build(): SwaggerSchemaEndpoint {
                return DefaultSwaggerSchemaEndpoint(
                    name = name
                            ?: throw IllegalArgumentException(
                                "swagger_schema_endpoint must have a name provided"
                            ),
                    webClientCustomizer = webClientCustomizer,
                    httpMethod = httpMethod,
                    uriCustomizer = uriCustomizer,
                    requestBodyCreator = requestBodyCreator,
                    responseOpenApiSpecificationJsonPath = responseOpenApiSpecificationJsonPath,
                    responseJsonNodePreprocessor = responseJsonNodePreprocessor,
                )
            }
        }
    }

    override fun uriCustomizer(): (UriBuilder) -> URI {
        return uriCustomizer
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
