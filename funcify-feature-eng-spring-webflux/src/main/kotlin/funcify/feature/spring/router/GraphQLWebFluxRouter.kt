package funcify.feature.spring.router

import arrow.core.None
import arrow.core.getOrElse
import arrow.core.some
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
class GraphQLWebFluxRouter(val graphQLRequestExecutor: GraphQLRequestExecutor,
                           val graphQLExecutionInputCustomizers: List<GraphQLExecutionInputCustomizer>) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(GraphQLWebFluxRouter::class.java)
        private const val MEDIA_TYPE_APPLICATION_GRAPHQL_VALUE = "application/graphql"
        private val STR_KEY_MAP_PARAMETERIZED_TYPE_REF = object : ParameterizedTypeReference<Map<String, Any?>>() {}
    }

    fun graphql(request: ServerRequest): Mono<ServerResponse> {
        val rawGraphQLRequestMono: Mono<RawGraphQLRequest> = convertServerRequestIntoRawGraphQLRequest(request)
        return Async.fromMono(rawGraphQLRequestMono)
                .flatMap { rawReq -> graphQLRequestExecutor.executeRequest(rawReq) }
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
                request.bodyToMono(String::class.java)
                        .flatMap { gqlText ->
                            gqlText.toOption()
                                    .map { rawText ->
                                        Mono.just(rawText)
                                    }
                                    .getOrElse { Mono.error(IllegalArgumentException("raw_graphql_input_text is null")) }
                        }
                        .map { txt ->
                            DefaultRawGraphQLRequest(uri = request.uri(),
                                                     headers = request.headers(),
                                                     rawGraphQLQueryText = txt,
                                                     locale = extractLocaleFromRequest(request),
                                                     executionInputCustomizers = graphQLExecutionInputCustomizers
                                                    )

                        }


            }
            MediaType.APPLICATION_JSON_VALUE == request.headers()
                    .firstHeader(HttpHeaders.CONTENT_TYPE) -> {
                request.bodyToMono(JsonNode::class.java)
                        .flatMap { jn ->
                            jn.toOption()
                                    .map { j -> Mono.just(j) }
                                    .getOrElse { Mono.error(IllegalArgumentException("could not extract json_node from content-type of request_body")) }
                        }
                        .map { jn ->
                            DefaultRawGraphQLRequest(uri = request.uri(),
                                                     headers = request.headers(),
                                                     operationName = jn.findPath("operationName")
                                                             .asText(""),
                                                     rawGraphQLQueryText = jn.findPath("query")
                                                             .asText(""),
                                                     variables = extractGraphQLVariablesFromJson(jn),
                                                     locale = extractLocaleFromRequest(request),
                                                     executionInputCustomizers = graphQLExecutionInputCustomizers
                                                    )
                        }
            }
            else -> {
                request.bodyToMono(STR_KEY_MAP_PARAMETERIZED_TYPE_REF)
                        .flatMap { map ->
                            map.toOption()
                                    .map { m -> Mono.just(m) }
                                    .getOrElse { Mono.error(IllegalArgumentException("could not extract string_object map from content-type of request_body")) }
                        }
                        .map { map ->
                            DefaultRawGraphQLRequest(uri = request.uri(),
                                                     headers = request.headers(),
                                                     operationName = map["operationName"].toOption()
                                                             .map { o -> o as String }
                                                             .getOrElse { "" },
                                                     rawGraphQLQueryText = map["query"].toOption()
                                                             .map { o -> o as String }
                                                             .getOrElse { "" },
                                                     variables = extractGraphQLVariablesFromStringKeyValueMap(map),
                                                     locale = extractLocaleFromRequest(request),
                                                     executionInputCustomizers = graphQLExecutionInputCustomizers
                                                    )
                        }
            }
        }
    }

    private fun extractGraphQLVariablesFromStringKeyValueMap(map: Map<String, Any?>): Map<String, Any> =
            map["variables"].toOption()
                    .filter { m -> m is Map<*, *> }
                    .map { m -> m as Map<*, *> }
                    .map { m -> m.entries }
                    .map { es ->
                        es.asSequence()
                                .map { e ->
                                    e.value.toOption()
                                            .fold({ None },
                                                  { v ->
                                                      (e.key.toString() to v).some()
                                                  })
                                }
                                .flatMap { opt ->
                                    opt.fold({ emptySequence() },
                                             { p -> sequenceOf(p) })
                                }
                                .fold(mapOf<String, Any>(),
                                      { m, p -> m.plus(p) })
                    }
                    .getOrElse { mapOf<String, Any>() }

    private fun extractGraphQLVariablesFromJson(jsonNode: JsonNode): Map<String, Any> =
            jsonNode.findPath("variables")
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

    private fun extractLocaleFromRequest(request: ServerRequest) =
            request.exchange().localeContext.toOption()
                    .flatMap { lc -> lc.locale.toOption() }
                    .getOrElse { Locale.getDefault() }
}