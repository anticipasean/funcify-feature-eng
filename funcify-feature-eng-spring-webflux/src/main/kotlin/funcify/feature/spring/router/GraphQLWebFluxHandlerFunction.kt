package funcify.feature.spring.router

import arrow.core.getOrElse
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.graphql.request.DefaultRawGraphQLRequest
import funcify.feature.graphql.request.GraphQLExecutionInputCustomizer
import funcify.feature.graphql.request.RawGraphQLRequest
import funcify.feature.graphql.response.SerializedGraphQLResponse
import funcify.feature.graphql.service.GraphQLRequestExecutor
import funcify.feature.tools.container.async.Async
import funcify.feature.tools.extensions.OptionExtensions.flatMapOptions
import funcify.feature.tools.extensions.PersistentMapExtensions.reduceEntriesToPersistentMap
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.HandlerFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import reactor.core.publisher.Mono
import java.util.Locale


/**
 *
 * @author smccarron
 * @created 2/19/22
 */
@Component
class GraphQLWebFluxHandlerFunction(val graphQLRequestExecutor: GraphQLRequestExecutor,
                                    val graphQLExecutionInputCustomizers: List<GraphQLExecutionInputCustomizer>) : HandlerFunction<ServerResponse> {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(GraphQLWebFluxHandlerFunction::class.java)
        private const val QUERY_KEY = "query"
        private const val GRAPHQL_REQUEST_VARIABLES_KEY = "variables"
        private const val OPERATION_NAME_KEY = "operationName"
        private const val MEDIA_TYPE_APPLICATION_GRAPHQL_VALUE = "application/graphql"
        private val STR_KEY_MAP_PARAMETERIZED_TYPE_REF = object : ParameterizedTypeReference<Map<String, Any?>>() {}

        private fun extractLocaleFromRequest(request: ServerRequest) =
                request.exchange().localeContext.toOption()
                        .flatMap { lc -> lc.locale.toOption() }
                        .getOrElse { Locale.getDefault() }

        private fun <T> T?.toMono(nullableParameterName: String): Mono<T> {
            return this.toOption()
                    .map { t -> Mono.just(t) }
                    .getOrElse {
                        val message = """
                                  |parameter $nullableParameterName is null but 
                                  |is required for processing this request successfully
                                  """.flattenIntoOneLine()
                        Mono.error(IllegalArgumentException(message))
                    }
        }
    }

    override fun handle(request: ServerRequest): Mono<ServerResponse> {
        val rawGraphQLRequestMono: Mono<RawGraphQLRequest> = convertServerRequestIntoRawGraphQLRequest(request)
        return Async.fromMono(rawGraphQLRequestMono)
                .flatMap { rawReq: RawGraphQLRequest -> graphQLRequestExecutor.executeSingleRequest(rawReq) }
                .map { serRes: SerializedGraphQLResponse -> serRes.executionResult.toSpecification() }
                .toFlux()
                .reduceWith({ -> persistentMapOf<String, Any?>() },
                            { pm: PersistentMap<String, Any?>, resultMap: MutableMap<String, Any?> ->
                                pm.putAll(resultMap)
                            })
                .flatMap { pm: ImmutableMap<String, Any?> ->
                    ServerResponse.ok()
                            .bodyValue(pm)
                }
                .single()
                .onErrorResume({ err ->
                                   ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                           .body(Mono.just(err.message.toOption()
                                                                   .getOrElse { "error_message empty" }))
                               })
    }

    private fun convertServerRequestIntoRawGraphQLRequest(request: ServerRequest): Mono<RawGraphQLRequest> {
        return when {
            MEDIA_TYPE_APPLICATION_GRAPHQL_VALUE == request.headers()
                    .firstHeader(HttpHeaders.CONTENT_TYPE) -> {
                transformRawGraphQLOperationTextIntoRawGraphQLRequest(request)
            }
            MediaType.APPLICATION_JSON_VALUE == request.headers()
                    .firstHeader(HttpHeaders.CONTENT_TYPE) -> {
                transformJsonIntoRawGraphQLRequest(request)
            }
            else -> {
                transformStringKeyMapIntoRawGraphQLRequest(request)
            }
        }
    }

    private fun transformStringKeyMapIntoRawGraphQLRequest(request: ServerRequest): Mono<RawGraphQLRequest> =
            request.bodyToMono(STR_KEY_MAP_PARAMETERIZED_TYPE_REF)
                    .flatMap { map ->
                        map.toMono("string_key_map")
                    }
                    .map { map ->
                        DefaultRawGraphQLRequest(uri = request.uri(),
                                                 headers = extractReadOnlyHttpHeadersFromRequest(request),
                                                 operationName = map[OPERATION_NAME_KEY].toOption()
                                                         .map { o -> o as String }
                                                         .getOrElse { "" },
                                                 rawGraphQLQueryText = map[QUERY_KEY].toOption()
                                                         .map { o -> o as String }
                                                         .getOrElse { "" },
                                                 variables = extractGraphQLVariablesFromStringKeyValueMap(map),
                                                 locale = extractLocaleFromRequest(request),
                                                 executionInputCustomizers = graphQLExecutionInputCustomizers)
                    }

    private fun transformJsonIntoRawGraphQLRequest(request: ServerRequest): Mono<RawGraphQLRequest> =
            request.bodyToMono(JsonNode::class.java)
                    .flatMap { jn ->
                        jn.toMono("json_node_form_of_input")
                    }
                    .map { jn ->
                        DefaultRawGraphQLRequest(uri = request.uri(),
                                                 headers = extractReadOnlyHttpHeadersFromRequest(request),
                                                 operationName = jn.findPath(OPERATION_NAME_KEY)
                                                         .asText(""),
                                                 rawGraphQLQueryText = jn.findPath(QUERY_KEY)
                                                         .asText(""),
                                                 variables = extractGraphQLVariablesFromJson(jn),
                                                 locale = extractLocaleFromRequest(request),
                                                 executionInputCustomizers = graphQLExecutionInputCustomizers)
                    }

    private fun transformRawGraphQLOperationTextIntoRawGraphQLRequest(request: ServerRequest): Mono<RawGraphQLRequest> =
            request.bodyToMono(String::class.java)
                    .flatMap { gqlText ->
                        gqlText.toMono("raw_graphql_text_input")
                    }
                    .map { txt ->
                        DefaultRawGraphQLRequest(uri = request.uri(),
                                                 headers = extractReadOnlyHttpHeadersFromRequest(request),
                                                 rawGraphQLQueryText = txt,
                                                 locale = extractLocaleFromRequest(request),
                                                 executionInputCustomizers = graphQLExecutionInputCustomizers)

                    }

    private fun extractGraphQLVariablesFromStringKeyValueMap(map: Map<String, Any?>): Map<String, Any?> =
            map[GRAPHQL_REQUEST_VARIABLES_KEY].toOption()
                    .filter { m -> m is Map<*, *> }
                    .map { m -> m as Map<*, *> }
                    .map { m -> m.entries }
                    .map { entrySet: Set<Map.Entry<Any?, Any?>> ->
                        entrySet.parallelStream()
                                .map { e ->
                                    e.value.toOption()
                                            .map { v -> e.key.toString() to v }
                                }
                                .flatMapOptions()
                                .reducePairsToPersistentMap()
                    }
                    .getOrElse { persistentMapOf<String, Any?>() }

    private fun extractGraphQLVariablesFromJson(jsonNode: JsonNode): Map<String, Any?> =
            jsonNode.findPath(GRAPHQL_REQUEST_VARIABLES_KEY)
                    .toOption()
                    .filter(JsonNode::isObject)
                    .map { jn ->
                        jn.fields()
                                .reduceEntriesToPersistentMap()
                    }
                    .getOrElse { persistentMapOf<String, Any?>() }


    private fun extractReadOnlyHttpHeadersFromRequest(request: ServerRequest): HttpHeaders {
        return HttpHeaders.readOnlyHttpHeaders(request.headers()
                                                       .asHttpHeaders());
    }
}