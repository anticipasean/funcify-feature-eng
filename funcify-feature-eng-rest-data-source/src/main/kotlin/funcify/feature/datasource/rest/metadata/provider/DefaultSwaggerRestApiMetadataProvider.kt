package funcify.feature.datasource.rest.metadata.provider

import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.datasource.rest.RestApiService
import funcify.feature.datasource.rest.error.RestApiDataSourceException
import funcify.feature.datasource.rest.error.RestApiErrorResponse
import funcify.feature.datasource.rest.swagger.SwaggerSchemaEndpoint
import funcify.feature.datasource.rest.swagger.SwaggerSchemaEndpointRegistry
import funcify.feature.json.JsonMapper
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.models.ParseOptions
import io.swagger.v3.parser.core.models.SwaggerParseResult
import java.nio.charset.StandardCharsets
import org.slf4j.Logger
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2022-07-09
 */
internal class DefaultSwaggerRestApiMetadataProvider(
    private val jsonMapper: JsonMapper,
    private val swaggerSchemaEndpointRegistry: SwaggerSchemaEndpointRegistry
) : SwaggerRestApiMetadataProvider {

    companion object {
        private val logger: Logger = loggerFor<DefaultSwaggerRestApiMetadataProvider>()
    }

    override fun provideMetadata(service: RestApiService): Mono<OpenAPI> {
        logger.info(
            """provide_metadata: [ service: 
            |{ name: ${service.serviceName}, 
            |host: ${service.hostName}, 
            |port: ${service.port} } ]
            |""".flatten()
        )
        return swaggerSchemaEndpointRegistry
            .getSwaggerSchemaEndpointForRestApiService(service)
            .successIfDefined {
                RestApiDataSourceException(
                    RestApiErrorResponse.REST_API_DATA_SOURCE_CREATION_ERROR,
                    """${SwaggerSchemaEndpoint::class.qualifiedName} not found  
                            |for rest_api_service: [ name: ${service.serviceName} ]
                            |""".flatten()
                )
            }
            .toMono()
            .flatMap { swaggerSchemaEndpoint ->
                requestSwaggerSchemaFromRestServiceOnSwaggerSchemaEndpoint(
                    service,
                    swaggerSchemaEndpoint
                )
            }
            .flatMap { swaggerJsonNode ->
                extractOpenAPISchemaFromJsonNodeResponseBody(swaggerJsonNode, service)
            }
    }

    private fun requestSwaggerSchemaFromRestServiceOnSwaggerSchemaEndpoint(
        service: RestApiService,
        swaggerSchemaEndpoint: SwaggerSchemaEndpoint,
    ): Mono<JsonNode> {
        val builder: WebClient.Builder = service.getWebClient().mutate()
        swaggerSchemaEndpoint.webClientCustomizer().customize(builder)
        val updatedWebClient = builder.build()
        return when (swaggerSchemaEndpoint.httpMethod) {
                HttpMethod.GET -> {
                    updatedWebClient
                        .get()
                        .uri(swaggerSchemaEndpoint.uriCustomizer())
                        .accept(MediaType.APPLICATION_JSON)
                        .acceptCharset(StandardCharsets.UTF_8)
                        .retrieve()
                }
                HttpMethod.POST -> {
                    updatedWebClient
                        .post()
                        .uri(swaggerSchemaEndpoint.uriCustomizer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .acceptCharset(StandardCharsets.UTF_8)
                        .body(
                            Mono.from(swaggerSchemaEndpoint.requestBodyCreator().invoke())
                                .flatMap { jn: JsonNode ->
                                    jsonMapper.fromJsonNode(jn).toJsonString().toMono()
                                },
                            String::class.java
                        )
                        .retrieve()
                }
                else -> {
                    throw RestApiDataSourceException(
                        RestApiErrorResponse.REST_API_DATA_SOURCE_CREATION_ERROR,
                        """unsupported http_method specified for use in calling 
                           |swagger_schema_endpoint: [ http_method: 
                           |${swaggerSchemaEndpoint.httpMethod}, 
                           |swagger_schema_endpoint.name: ${swaggerSchemaEndpoint.name} 
                           |]""".flatten()
                    )
                }
            }
            .onStatus(
                { httpStatus: HttpStatus ->
                    httpStatus.is4xxClientError || httpStatus.is5xxServerError
                },
                { cr: ClientResponse ->
                    cr.bodyToMono(String::class.java).map { errorMessage: String ->
                        RestApiDataSourceException(
                            RestApiErrorResponse.REST_API_DATA_SOURCE_CREATION_ERROR,
                            """swagger_schema_endpoint on service [ name: ${service.serviceName} ] 
                              |did not successfully return a swagger_schema because: 
                              |[ response_code: ${cr.statusCode()}, message: ${errorMessage} ]
                              |""".flatten()
                        )
                    }
                }
            )
            .bodyToMono(JsonNode::class.java)
            .flatMap { responseJsonNode: JsonNode ->
                applySwaggerResponseJsonPreprocessingForSwaggerSchemaEndpoint(
                    responseJsonNode,
                    swaggerSchemaEndpoint
                )
            }
    }

    private fun applySwaggerResponseJsonPreprocessingForSwaggerSchemaEndpoint(
        responseJsonNode: JsonNode,
        swaggerSchemaEndpoint: SwaggerSchemaEndpoint,
    ): Mono<out JsonNode> {
        return jsonMapper
            .fromJsonNode(responseJsonNode)
            .toJsonNodeForPath(swaggerSchemaEndpoint.responseOpenApiSpecificationJsonPath)
            .map { swaggerPortionOfJsonResponse ->
                swaggerSchemaEndpoint
                    .responseOpenApiSpecificationJsonNodeContentPreprocessor()
                    .invoke(swaggerPortionOfJsonResponse)
            }
            .toMono()
    }

    private fun extractOpenAPISchemaFromJsonNodeResponseBody(
        swaggerJsonNode: JsonNode,
        service: RestApiService,
    ): Mono<out OpenAPI> {
        return Try.attempt {
                OpenAPIV3Parser().parseJsonNode(null, swaggerJsonNode, ParseOptions())
            }
            .peekIfSuccess { swaggerParseResult: SwaggerParseResult ->
                if (!swaggerParseResult.messages.isNullOrEmpty()) {
                    val errorMessageSet: String =
                        swaggerParseResult.messages.joinToString("\n", "[ ", " ]")
                    val logMessage =
                        """extract_openapi_schema_from_json_node_response_body: 
                           |[ warning: swagger_parse_result contains some error messages 
                           |for parsing swagger schema of [ service.name: ${service.serviceName} ]
                           |[ messages: $errorMessageSet ] 
                           |]""".flatten()
                    logger.warn(logMessage)
                }
            }
            .map { swaggerParseResult: SwaggerParseResult -> swaggerParseResult.openAPI }
            .toMono()
    }
}
