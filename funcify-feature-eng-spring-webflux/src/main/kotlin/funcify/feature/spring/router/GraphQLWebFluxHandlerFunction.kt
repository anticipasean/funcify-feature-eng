package funcify.feature.spring.router

import arrow.core.getOrElse
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.error.FeatureEngCommonException
import funcify.feature.json.JsonMapper
import funcify.feature.materializer.request.GraphQLExecutionInputCustomizer
import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.request.RawGraphQLRequestFactory
import funcify.feature.materializer.response.SerializedGraphQLResponse
import funcify.feature.spring.service.GraphQLSingleRequestExecutor
import funcify.feature.tools.container.deferred.Deferred
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.PersistentMapExtensions.reduceEntriesToPersistentMap
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.StreamExtensions.flatMapOptions
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import graphql.GraphQLError
import java.util.*
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.slf4j.Logger
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.HandlerFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2/19/22
 */
internal class GraphQLWebFluxHandlerFunction(
    private val jsonMapper: JsonMapper,
    private val graphQLSingleRequestExecutor: GraphQLSingleRequestExecutor,
    private val rawGraphQLRequestFactory: RawGraphQLRequestFactory,
    private val graphQLExecutionInputCustomizers: List<GraphQLExecutionInputCustomizer>
) : HandlerFunction<ServerResponse> {

    companion object {
        private val logger: Logger = loggerFor<GraphQLWebFluxHandlerFunction>()
        private const val QUERY_KEY = "query"
        private const val GRAPHQL_REQUEST_VARIABLES_KEY = "variables"
        private const val OPERATION_NAME_KEY = "operationName"
        private const val MEDIA_TYPE_APPLICATION_GRAPHQL_VALUE = "application/graphql"
        private val STR_KEY_MAP_PARAMETERIZED_TYPE_REF =
            object : ParameterizedTypeReference<Map<String, Any?>>() {}

        private fun extractLocaleFromRequest(request: ServerRequest): Locale {
            return request
                .exchange()
                .localeContext
                .toOption()
                .flatMap { lc -> lc.locale.toOption() }
                .getOrElse { Locale.getDefault() }
        }

        private fun <T> T?.toMono(nullableParameterName: String): Mono<T> {
            return this.toOption()
                .map { t -> Mono.just(t) }
                .getOrElse {
                    val message =
                        """[ parameter: $nullableParameterName ] is null but 
                       |is required for processing this request successfully
                       |""".flattenIntoOneLine()
                    Mono.error(IllegalArgumentException(message))
                }
        }
    }

    override fun handle(request: ServerRequest): Mono<ServerResponse> {
        logger.info("handle: [ request.path: ${request.path()} ]")
        val rawGraphQLRequestMono: Mono<RawGraphQLRequest> =
            convertServerRequestIntoRawGraphQLRequest(request)
        return Deferred.fromMono(rawGraphQLRequestMono)
            .flatMap { rawReq: RawGraphQLRequest ->
                graphQLSingleRequestExecutor.executeSingleRequest(rawReq)
            }
            .map { serRes: SerializedGraphQLResponse -> serRes.executionResult.toSpecification() }
            .toFlux()
            .reduceWith(
                { -> persistentMapOf<String, Any?>() },
                { pm: PersistentMap<String, Any?>, resultMap: MutableMap<String, Any?> ->
                    pm.putAll(resultMap)
                }
            )
            .flatMap { pm: ImmutableMap<String, Any?> -> ServerResponse.ok().bodyValue(pm) }
            .single()
            .onErrorResume(
                FeatureEngCommonException::class.java,
                convertCommonExceptionTypeIntoServerResponse()
            )
            .onErrorResume { err ->
                ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Mono.just(err.message.toOption().getOrElse { "error_message empty" }))
            }
    }

    // TODO: Currently has two different output formats: direct message and graphql_error_map_string
    // Should be made into one
    private fun convertCommonExceptionTypeIntoServerResponse():
        (FeatureEngCommonException) -> Mono<out ServerResponse> {
        return { commonException: FeatureEngCommonException ->
            when {
                commonException.errorResponse.responseIfGraphQL.isDefined() -> {
                    val graphQLError: GraphQLError =
                        commonException.errorResponse.responseIfGraphQL.orNull()!!
                    commonException.errorResponse.responseStatusIfHttp
                        .map { httpStatus: HttpStatus -> ServerResponse.status(httpStatus) }
                        .getOrElse { ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR) }
                        .bodyValue(
                            jsonMapper
                                .fromKotlinObject(graphQLError.toSpecification())
                                .toJsonString()
                                .orElse(commonException.inputMessage)
                        )
                }
                else -> {
                    commonException.errorResponse.responseStatusIfHttp
                        .map { httpStatus: HttpStatus -> ServerResponse.status(httpStatus) }
                        .getOrElse { ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR) }
                        .bodyValue(commonException.inputMessage)
                }
            }
        }
    }

    private fun convertServerRequestIntoRawGraphQLRequest(
        request: ServerRequest
    ): Mono<RawGraphQLRequest> {
        return when (request.headers().firstHeader(HttpHeaders.CONTENT_TYPE)) {
            MEDIA_TYPE_APPLICATION_GRAPHQL_VALUE -> {
                transformRawGraphQLOperationTextIntoRawGraphQLRequest(request)
            }
            MediaType.APPLICATION_JSON_VALUE -> {
                transformJsonIntoRawGraphQLRequest(request)
            }
            else -> {
                transformStringKeyMapIntoRawGraphQLRequest(request)
            }
        }
    }

    private fun transformStringKeyMapIntoRawGraphQLRequest(
        request: ServerRequest
    ): Mono<RawGraphQLRequest> {
        return request
            .bodyToMono(STR_KEY_MAP_PARAMETERIZED_TYPE_REF)
            .flatMap { map -> map.toMono("string_key_map") }
            .map { map ->
                rawGraphQLRequestFactory
                    .builder()
                    .uri(request.uri())
                    .headers(extractReadOnlyHttpHeadersFromRequest(request))
                    .operationName(
                        map[OPERATION_NAME_KEY].toOption().map { o -> o as String }.getOrElse { "" }
                    )
                    .rawGraphQLQueryText(
                        map[QUERY_KEY].toOption().map { o -> o as String }.getOrElse { "" }
                    )
                    .variables(extractGraphQLVariablesFromStringKeyValueMap(map))
                    .locale(extractLocaleFromRequest(request))
                    .executionInputCustomizers(graphQLExecutionInputCustomizers)
                    .build()
            }
    }

    private fun transformJsonIntoRawGraphQLRequest(
        request: ServerRequest
    ): Mono<RawGraphQLRequest> {
        return request
            .bodyToMono(JsonNode::class.java)
            .flatMap { jn -> jn.toMono("json_node_form_of_input") }
            .map { jn ->
                rawGraphQLRequestFactory
                    .builder()
                    .uri(request.uri())
                    .headers(extractReadOnlyHttpHeadersFromRequest(request))
                    .operationName(jn.findPath(OPERATION_NAME_KEY).asText(""))
                    .rawGraphQLQueryText(jn.findPath(QUERY_KEY).asText(""))
                    .variables(extractGraphQLVariablesFromJson(jn))
                    .locale(extractLocaleFromRequest(request))
                    .executionInputCustomizers(graphQLExecutionInputCustomizers)
                    .build()
            }
    }

    private fun transformRawGraphQLOperationTextIntoRawGraphQLRequest(
        request: ServerRequest
    ): Mono<RawGraphQLRequest> {
        return request
            .bodyToMono(String::class.java)
            .flatMap { gqlText -> gqlText.toMono("raw_graphql_text_input") }
            .map { txt ->
                rawGraphQLRequestFactory
                    .builder()
                    .uri(request.uri())
                    .headers(extractReadOnlyHttpHeadersFromRequest(request))
                    .rawGraphQLQueryText(txt)
                    .locale(extractLocaleFromRequest(request))
                    .executionInputCustomizers(graphQLExecutionInputCustomizers)
                    .build()
            }
    }

    private fun extractGraphQLVariablesFromStringKeyValueMap(
        map: Map<String, Any?>
    ): Map<String, Any?> {
        return map[GRAPHQL_REQUEST_VARIABLES_KEY]
            .toOption()
            .filter { m -> m is Map<*, *> }
            .map { m -> m as Map<*, *> }
            .map { m -> m.entries }
            .map { entrySet: Set<Map.Entry<Any?, Any?>> ->
                entrySet
                    .parallelStream()
                    .map { e -> e.value.toOption().map { v -> e.key.toString() to v } }
                    .flatMapOptions()
                    .reducePairsToPersistentMap()
            }
            .getOrElse { persistentMapOf<String, Any?>() }
    }

    private fun extractGraphQLVariablesFromJson(jsonNode: JsonNode): Map<String, Any?> {
        return jsonNode
            .findPath(GRAPHQL_REQUEST_VARIABLES_KEY)
            .toOption()
            .filter(JsonNode::isObject)
            .map { jn -> jn.fields().reduceEntriesToPersistentMap() }
            .getOrElse { persistentMapOf<String, Any?>() }
    }

    private fun extractReadOnlyHttpHeadersFromRequest(request: ServerRequest): HttpHeaders {
        return HttpHeaders.readOnlyHttpHeaders(request.headers().asHttpHeaders())
    }
}
