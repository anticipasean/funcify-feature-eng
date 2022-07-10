package funcify.feature.datasource.rest.swagger

import arrow.core.identity
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.jayway.jsonpath.JsonPath
import funcify.feature.datasource.rest.RestApiService
import funcify.feature.tools.container.deferred.Deferred
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.OpenAPIV3Parser
import org.reactivestreams.Publisher
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer
import org.springframework.http.HttpMethod
import org.springframework.web.reactive.function.client.WebClient

/**
 * Represents an endpoint specification for [RestApiService]s that support [OpenAPI] models of their
 * APIs
 * @author smccarron
 * @created 2022-07-09
 */
interface SwaggerSchemaEndpoint {

    val name: String

    val httpMethod: HttpMethod

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

    /**
     * This function creates the request body for the schema endpoint if it is a [HttpMethod.POST]
     * request
     *
     * @default_value: a function that creates an empty [ObjectNode] and publisher for it
     */
    fun requestBodyCreator(): () -> Publisher<JsonNode> {
        return { -> Deferred.completed<JsonNode>(JsonNodeFactory.instance.objectNode()) }
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
}
