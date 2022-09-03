package funcify.feature.datasource.rest.swagger

import arrow.core.identity
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.jayway.jsonpath.JsonPath
import funcify.feature.datasource.rest.RestApiService
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.OpenAPIV3Parser
import java.net.URI
import org.reactivestreams.Publisher
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer
import org.springframework.http.HttpMethod
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriBuilder
import reactor.core.publisher.Mono

/**
 * Represents an endpoint specification for [RestApiService]s that support [OpenAPI] models of their
 * APIs
 * @author smccarron
 * @created 2022-07-09
 */
interface SwaggerSchemaEndpoint {

    companion object {

        fun builder(): Builder {
            return DefaultSwaggerSchemaEndpoint.Companion.DefaultBuilder()
        }
    }

    val name: String

    /**
     * This function is run on the [WebClient.mutate] for a given [RestApiService] before a
     * [WebClient] call is made to fetch the response containing the OpenAPI specification for this
     * [RestApiService]
     *
     * @default_value: an empty customizer that doesn't make any changes to the given
     * [WebClient.Builder]
     */
    fun webClientCustomizer(): WebClientCustomizer {
        return WebClientCustomizer {}
    }

    val httpMethod: HttpMethod

    /**
     * This function is run on the [WebClient.RequestBodyUriSpec] after
     * [SwaggerSchemaEndpoint.webClientCustomizer] has been applied and the http method chosen
     */
    fun uriCustomizer(): (UriBuilder) -> URI {
        return { uriBuilder: UriBuilder -> uriBuilder.build() }
    }

    /**
     * This function creates the request body for the schema endpoint if it is a [HttpMethod.POST]
     * request
     *
     * @default_value: a function that creates an empty [ObjectNode] and publisher for it
     */
    fun requestBodyCreator(): () -> Publisher<JsonNode> {
        return { -> Mono.just<JsonNode>(JsonNodeFactory.instance.objectNode()) }
    }

    /**
     * Locates the part of the [JsonNode] response from the [WebClient] of the [RestApiService] that
     * should be used as the "contents" for the [OpenAPIV3Parser.readContents] invocation
     *
     * @default_value: the root path "$" in the com.jayway.jsonpath framework
     */
    val responseOpenApiSpecificationJsonPath: JsonPath
        get() = JsonPath.compile("$")

    /**
     * This preprocessing function is run on the output [JsonNode] instance for the [JsonPath.read]
     * before submission to [OpenAPIV3Parser.readContents] as a [String]
     *
     * @default_value: a preprocessor that does not make any changes to the given [JsonNode]
     */
    fun responseOpenApiSpecificationJsonNodeContentPreprocessor(): (JsonNode) -> (JsonNode) {
        return ::identity
    }

    interface Builder {

        fun name(name: String): Builder

        fun webClientCustomizer(webClientCustomizer: WebClientCustomizer): Builder

        fun httpMethod(httpMethod: HttpMethod): Builder

        fun uriCustomizer(uriCustomizer: (UriBuilder) -> URI): Builder

        fun requestBodyCreator(requestBodyCreator: () -> Publisher<JsonNode>): Builder

        fun responseOpenApiSpecificationJsonPath(
            responseOpenApiSpecificationJsonPath: JsonPath
        ): Builder

        fun responseJsonNodePreprocessor(
            responseJsonNodePreprocessor: (JsonNode) -> JsonNode
        ): Builder

        fun build(): SwaggerSchemaEndpoint
    }
}
