package funcify.feature.spring.router

import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.graphql.request.DefaultRawGraphQLRequest
import funcify.feature.graphql.request.GraphQLExecutionInputCustomizer
import funcify.feature.graphql.request.RawGraphQLRequest
import funcify.feature.graphql.service.GraphQLRequestExecutor
import funcify.feature.tools.container.async.Async
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

        private fun <T> Option<T>.toSequence(): Sequence<T> {
            return this.fold({ emptySequence<T>() },
                             { t -> sequenceOf(t) })
        }

        private fun <T> T?.toMono(nullableParameterName: String): Mono<T> {
            return this.toOption()
                    .map { t -> Mono.just(t) }
                    .getOrElse {
                        val message = """
                                  |parameter $nullableParameterName is null but 
                                  |is required for processing this request successfully
                                  """.trimMargin()
                        Mono.error(IllegalArgumentException(message))
                    }
        }
    }

    override fun handle(request: ServerRequest): Mono<ServerResponse> {
        val rawGraphQLRequestMono: Mono<RawGraphQLRequest> = convertServerRequestIntoRawGraphQLRequest(request)
        return Async.fromMono(rawGraphQLRequestMono)
                .flatMap { rawReq -> graphQLRequestExecutor.executeSingleRequest(rawReq) }
                .map { serRes -> serRes.executionResult.toSpecification() }
                .toFlux()
                .flatMap { spec ->
                    ServerResponse.ok()
                            .bodyValue(spec)
                }
                .single()
                .onErrorResume({ err ->
                                   ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                           .body(Mono.just(err.message.toOption()
                                                                   .getOrElse { "error_message empty" })
                                                )
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
                                                 headers = request.headers(),
                                                 operationName = map[OPERATION_NAME_KEY].toOption()
                                                         .map { o -> o as String }
                                                         .getOrElse { "" },
                                                 rawGraphQLQueryText = map[QUERY_KEY].toOption()
                                                         .map { o -> o as String }
                                                         .getOrElse { "" },
                                                 variables = extractGraphQLVariablesFromStringKeyValueMap(map),
                                                 locale = extractLocaleFromRequest(request),
                                                 executionInputCustomizers = graphQLExecutionInputCustomizers
                                                )
                    }

    private fun transformJsonIntoRawGraphQLRequest(request: ServerRequest): Mono<RawGraphQLRequest> =
            request.bodyToMono(JsonNode::class.java)
                    .flatMap { jn ->
                        jn.toMono("json_node_form_of_input")
                    }
                    .map { jn ->
                        DefaultRawGraphQLRequest(uri = request.uri(),
                                                 headers = request.headers(),
                                                 operationName = jn.findPath(OPERATION_NAME_KEY)
                                                         .asText(""),
                                                 rawGraphQLQueryText = jn.findPath(QUERY_KEY)
                                                         .asText(""),
                                                 variables = extractGraphQLVariablesFromJson(jn),
                                                 locale = extractLocaleFromRequest(request),
                                                 executionInputCustomizers = graphQLExecutionInputCustomizers
                                                )
                    }

    private fun transformRawGraphQLOperationTextIntoRawGraphQLRequest(request: ServerRequest): Mono<RawGraphQLRequest> =
            request.bodyToMono(String::class.java)
                    .flatMap { gqlText ->
                        gqlText.toMono("raw_graphql_text_input")
                    }
                    .map { txt ->
                        DefaultRawGraphQLRequest(uri = request.uri(),
                                                 headers = request.headers(),
                                                 rawGraphQLQueryText = txt,
                                                 locale = extractLocaleFromRequest(request),
                                                 executionInputCustomizers = graphQLExecutionInputCustomizers
                                                )

                    }

    private fun extractGraphQLVariablesFromStringKeyValueMap(map: Map<String, Any?>): Map<String, Any> =
            map[GRAPHQL_REQUEST_VARIABLES_KEY].toOption()
                    .filter { m -> m is Map<*, *> }
                    .map { m -> m as Map<*, *> }
                    .map { m -> m.entries }
                    .map { es ->
                        es.asSequence()
                                .map { e ->
                                    e.value.toOption()
                                            .map { v -> e.key.toString() to v }
                                }
                                .flatMap { opt ->
                                    opt.toSequence()
                                }
                                .fold(mapOf<String, Any>(),
                                      { m, p -> m.plus(p) })
                    }
                    .getOrElse { mapOf<String, Any>() }

    private fun extractGraphQLVariablesFromJson(jsonNode: JsonNode): Map<String, Any> =
            jsonNode.findPath(GRAPHQL_REQUEST_VARIABLES_KEY)
                    .toOption()
                    .filter(JsonNode::isObject)
                    .map { jn ->
                        jn.fields()
                                .asSequence()
                                .map { e -> e.key to e.value }
                                .fold(mapOf<String, Any>(),
                                      { m, p -> m.plus(p) })
                    }
                    .getOrElse { mapOf<String, Any>() }


}