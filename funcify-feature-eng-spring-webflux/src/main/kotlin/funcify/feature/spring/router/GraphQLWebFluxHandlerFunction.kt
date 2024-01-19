package funcify.feature.spring.router

import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.identity
import arrow.core.none
import arrow.core.some
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import funcify.feature.error.ServiceError
import funcify.feature.materializer.executor.GraphQLSingleRequestExecutor
import funcify.feature.materializer.request.GraphQLExecutionInputCustomizer
import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.request.factory.RawGraphQLRequestFactory
import funcify.feature.materializer.response.SerializedGraphQLResponse
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.attempt.Try.Companion.filterInstanceOf
import funcify.feature.tools.container.attempt.Try.Companion.flatMapFailure
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.toMono
import funcify.feature.tools.extensions.PersistentMapExtensions.reduceEntriesToPersistentMap
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.StreamExtensions.flatMapOptions
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.ThrowableExtensions.possiblyNestedHeadStackTraceElement
import funcify.feature.tools.json.JsonMapper
import graphql.GraphQLError
import graphql.execution.AbortExecutionException
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import org.slf4j.Logger
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.messaging.MessageHeaders
import org.springframework.web.reactive.function.server.HandlerFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono
import reactor.core.publisher.Timed
import reactor.core.scheduler.Schedulers
import java.util.*

/**
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
        private const val OUTPUT_KEY = "output"
        private const val ERRORS_KEY = "errors"
        private const val OPERATION_NAME_KEY = "operationName"
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

        private fun <T : Any> T?.toMono(nullableParameterName: String): Mono<T> {
            return when (this) {
                null -> {
                    Mono.error<T> {
                        val message =
                            """[ parameter: $nullableParameterName ] is null but 
                            |is required for processing this request successfully
                            |"""
                                .flatten()
                        ServiceError.invalidRequestErrorBuilder().message(message).build()
                    }
                }
                else -> {
                    Mono.just<T>(this)
                }
            }
        }
    }

    override fun handle(request: ServerRequest): Mono<ServerResponse> {
        logger.info("handle: [ request.path: ${request.path()} ]")
        return Mono.defer { convertServerRequestIntoRawGraphQLRequest(request) }
            .publishOn(Schedulers.boundedElastic())
            .flatMap { rawReq: RawGraphQLRequest ->
                graphQLSingleRequestExecutor.executeSingleRequest(rawReq)
            }
            .flatMap(convertAggregateSerializedGraphQLResponseIntoServerResponse())
            .doOnError(logAnyErrorsBeforeCreatingServerResponse())
            .onErrorResume(ServiceError::class.java, convertCommonExceptionTypeIntoServerResponse())
            .onErrorResume(
                { t: Throwable -> t is GraphQLError },
                convertGraphQLErrorTypeIntoServerResponse()
            )
            .onErrorResume(convertAnyUnhandledExceptionsIntoServerResponse(request))
            .timed()
            .map { timedResponse: Timed<ServerResponse> ->
                val successOrFailureStatus: String =
                    if (timedResponse.get().statusCode().is2xxSuccessful) {
                        "success"
                    } else {
                        "failure"
                    }
                logger.info(
                    "handle: [ status: {} ] [ elapsed_time: {} ms, response_http_status: {} ]",
                    successOrFailureStatus,
                    timedResponse.elapsedSinceSubscription().toMillis(),
                    timedResponse.get().statusCode(),
                )
                timedResponse.get()
            }
    }

    private fun convertServerRequestIntoRawGraphQLRequest(
        request: ServerRequest
    ): Mono<out RawGraphQLRequest> {
        return when (request.headers().contentType().orElse(null)) {
            MediaType.APPLICATION_GRAPHQL_RESPONSE,
            MediaType.APPLICATION_GRAPHQL -> {
                transformRawGraphQLOperationTextIntoRawGraphQLRequest(request)
            }
            MediaType.APPLICATION_JSON -> {
                transformJsonIntoRawGraphQLRequest(request)
            }
            // TODO: Add support for persisted queries wherein Apollo-style hash has been submitted
            // in lieu of query text
            else -> {
                transformStringKeyMapIntoRawGraphQLRequest(request)
            }
        }
    }

    private fun transformStringKeyMapIntoRawGraphQLRequest(
        request: ServerRequest
    ): Mono<out RawGraphQLRequest> {
        return request
            .bodyToMono(STR_KEY_MAP_PARAMETERIZED_TYPE_REF)
            .flatMap { nullableStrKeyMap: Map<String, Any?>? ->
                nullableStrKeyMap.toMono("string_key_map")
            }
            .map { strKeyMap: Map<String, Any?> ->
                rawGraphQLRequestFactory
                    .builder()
                    .uri(request.uri())
                    .headers(extractMessageHeadersFromRequest(request))
                    .principalPublisher(request.principal())
                    .operationName(
                        strKeyMap[OPERATION_NAME_KEY]
                            .toOption()
                            .map { o -> o as String }
                            .getOrElse { "" }
                    )
                    .rawGraphQLQueryText(
                        strKeyMap[QUERY_KEY].toOption().map { o -> o as String }.getOrElse { "" }
                    )
                    .variables(extractGraphQLVariablesFromStringKeyValueMap(strKeyMap))
                    .locale(extractLocaleFromRequest(request))
                    .expectedOutputFieldNames(
                        strKeyMap[OUTPUT_KEY]
                            .toOption()
                            .filterIsInstance<List<String>>()
                            .getOrElse { emptyList() }
                    )
                    .executionInputCustomizers(graphQLExecutionInputCustomizers)
                    .build()
            }
    }

    private fun transformJsonIntoRawGraphQLRequest(
        request: ServerRequest
    ): Mono<out RawGraphQLRequest> {
        return request
            .bodyToMono<JsonNode?>(JsonNode::class.java)
            .flatMap { nullableQueryJson: JsonNode? ->
                nullableQueryJson.toMono("json_node_form_of_input")
            }
            .map { queryJson: JsonNode ->
                rawGraphQLRequestFactory
                    .builder()
                    .uri(request.uri())
                    .headers(extractMessageHeadersFromRequest(request))
                    .operationName(queryJson.findPath(OPERATION_NAME_KEY).asText(""))
                    .principalPublisher(request.principal())
                    .rawGraphQLQueryText(queryJson.findPath(QUERY_KEY).asText(""))
                    .variables(extractGraphQLVariablesFromJson(queryJson))
                    .locale(extractLocaleFromRequest(request))
                    .expectedOutputFieldNames(
                        queryJson
                            .findPath(OUTPUT_KEY)
                            .toOption()
                            .filterIsInstance<ArrayNode>()
                            .map { an ->
                                an.asSequence()
                                    .map { jn -> jn.asText("") }
                                    .filterNot { s -> s.isBlank() }
                                    .toList()
                            }
                            .getOrElse { emptyList() }
                    )
                    .executionInputCustomizers(graphQLExecutionInputCustomizers)
                    .build()
            }
    }

    private fun transformRawGraphQLOperationTextIntoRawGraphQLRequest(
        request: ServerRequest
    ): Mono<out RawGraphQLRequest> {
        return request
            .bodyToMono<String?>(String::class.java)
            .flatMap { nullableQueryText: String? ->
                nullableQueryText.toMono("raw_graphql_text_input")
            }
            .map { queryText: String ->
                rawGraphQLRequestFactory
                    .builder()
                    .uri(request.uri())
                    .headers(extractMessageHeadersFromRequest(request))
                    .principalPublisher(request.principal())
                    .rawGraphQLQueryText(queryText)
                    .variables(request.queryParams().toSingleValueMap())
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
                    .map { e ->
                        // Permit non-null key to nullable value pairs to pass
                        when (val key = e.key) {
                            null -> none()
                            else -> {
                                when (key) {
                                    is String -> (key to e.value).some()
                                    else -> (key.toString() to e.value).some()
                                }
                            }
                        }
                    }
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
            .flatMap { jn ->
                jsonMapper
                    .fromJsonNode(jn)
                    .toKotlinObject(STR_KEY_MAP_PARAMETERIZED_TYPE_REF)
                    .getSuccess()
            }
            .getOrElse { persistentMapOf<String, Any?>() }
    }

    private fun extractMessageHeadersFromRequest(request: ServerRequest): MessageHeaders {
        return MessageHeaders(
            request.headers().asHttpHeaders().asSequence().fold(persistentMapOf<String, Any?>()) {
                pm: PersistentMap<String, Any?>,
                (k: String, v: List<String>?) ->
                when (v?.size ?: 0) {
                    0 -> {
                        pm.put(k, null)
                    }
                    1 -> {
                        pm.put(k, v[0])
                    }
                    else -> {
                        pm.put(k, v.toPersistentList())
                    }
                }
            }
        )
    }

    private fun convertAggregateSerializedGraphQLResponseIntoServerResponse():
        (SerializedGraphQLResponse) -> Mono<out ServerResponse> {
        return { response ->
            when {
                response.resultAsColumnarJsonObject.isDefined() -> {
                    response.resultAsColumnarJsonObject
                        .zip(
                            Try.attemptSequence(
                                    response.executionResult.errors.asSequence().map { gqlError ->
                                        convertGraphQLErrorIntoErrorJsonNode(gqlError)
                                    }
                                )
                                .fold(::identity) { t: Throwable ->
                                    sequenceOf(
                                        JsonNodeFactory.instance
                                            .objectNode()
                                            .put("errorType", t::class.simpleName)
                                            .put("message", t.message)
                                    )
                                }
                                .fold(JsonNodeFactory.instance.arrayNode(), ArrayNode::add)
                                .some()
                        )
                        .map { (columnarJsonObject, graphQLErrors) ->
                            mapOf(OUTPUT_KEY to columnarJsonObject, ERRORS_KEY to graphQLErrors)
                        }
                        .toMono()
                        .flatMap { spec ->
                            ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(spec)
                        }
                }
                else -> {
                    Try.attempt { response.executionResult.toSpecification() }
                        .mapFailure { t: Throwable ->
                            val message: String =
                                """unable to convert graphql execution_result 
                                |into specification for api_response 
                                |[ type: Map<String, Any?> ] given cause: 
                                |[ type: ${t::class.qualifiedName}, 
                                |message: ${t.message} ]"""
                                    .flatten()
                            ServiceError.downstreamResponseErrorBuilder()
                                .message(message)
                                .cause(t)
                                .build()
                        }
                        .toMono()
                        .flatMap { spec ->
                            ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(spec)
                        }
                }
            }
        }
    }

    private fun logAnyErrorsBeforeCreatingServerResponse(): (Throwable) -> Unit {
        return { t: Throwable ->
            when (t) {
                is ServiceError -> {
                    logger.error(
                        "handle: [ error(s) occurred ] [ type: {}, json: {} ]",
                        t::class.simpleName,
                        t.toJsonNode()
                    )
                }
                else -> {
                    logger.error(
                        "handle: [ error(s) occurred ] [ type: {}, message: {}, stack_trace_element[0]: {} ]",
                        t::class.simpleName,
                        t.message,
                        t.possiblyNestedHeadStackTraceElement()
                    )
                }
            }
        }
    }

    private fun convertCommonExceptionTypeIntoServerResponse():
        (ServiceError) -> Mono<out ServerResponse> {
        return { serviceError: ServiceError ->
            ServerResponse.status(serviceError.serverHttpResponse)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    mapOf<String, JsonNode>(
                        ERRORS_KEY to
                            JsonNodeFactory.instance.arrayNode().add(serviceError.toJsonNode())
                    )
                )
        }
    }

    private fun convertGraphQLErrorWithinCommonExceptionIntoErrorJsonNode(
        graphQLError: GraphQLError,
        serviceError: ServiceError,
    ): JsonNode {
        return convertGraphQLErrorIntoErrorJsonNode(graphQLError).fold(::identity) { _: Throwable ->
            jsonMapper
                .fromKotlinObject(
                    mapOf("errorType" to graphQLError.errorType, "message" to serviceError.message)
                )
                .toJsonNode()
                .orElseGet { JsonNodeFactory.instance.nullNode() }
        }
    }

    private fun convertGraphQLErrorIntoErrorJsonNode(graphQLError: GraphQLError): Try<JsonNode> {
        return Try.attempt { graphQLError.toSpecification() }
            .map(removeCauseFromSpecIfPresentLoggingWarning())
            .flatMap { spec -> jsonMapper.fromKotlinObject(spec).toJsonNode() }
            .flatMapFailure { t: Throwable ->
                if (
                    graphQLError.locations
                        .toOption()
                        .filter { locs -> locs.any { srcLoc -> srcLoc == null } }
                        .isDefined()
                ) {
                    jsonMapper
                        .fromKotlinObject(
                            mapOf(
                                "errorType" to graphQLError.errorType,
                                "message" to graphQLError.message,
                                "path" to graphQLError.path,
                                "extensions" to graphQLError.extensions
                            )
                        )
                        .toJsonNode()
                } else {
                    Try.failure(t)
                }
            }
    }

    private fun removeCauseFromSpecIfPresentLoggingWarning():
        (Map<String, Any?>) -> Map<String, Any?> {
        return { spec ->
            if (spec.containsKey("exception")) {
                val causeOpt: Option<Throwable> =
                    spec.get("exception").toOption().filterIsInstance<Throwable>()
                logger.warn(
                    """
                    |convert_graphql_error_into_error_json_node: 
                    |[ status: removing 'cause' from spec for brevity in response payload ]
                    |[ cause: { type: {}, message: {} ]
                    """
                        .flatten(),
                    causeOpt.map { t -> t::class.simpleName }.getOrElse { "<NA>" },
                    causeOpt.map { t -> t.message }.getOrElse { "<NA>" }
                )
                spec
                    .asSequence()
                    .filterNot { (k, _) -> k == "exception" }
                    .reduceEntriesToPersistentMap()
            } else {
                spec
            }
        }
    }

    private fun convertGraphQLErrorTypeIntoServerResponse():
        (Throwable) -> Mono<out ServerResponse> {
        return { t: Throwable ->
            val errorNodeToErrorsMapConverter: (GraphQLError) -> Map<String, Any?> = { gqlError ->
                mapOf(
                    "errors" to
                        JsonNodeFactory.instance
                            .arrayNode()
                            .add(
                                convertGraphQLErrorWithinCommonExceptionIntoErrorJsonNode(
                                    gqlError,
                                    ServiceError.downstreamResponseErrorBuilder()
                                        .message(
                                            """a serialized_graphql_response was not received 
                                        |from upstream likely due to aborted execution"""
                                                .flatten()
                                        )
                                        .build()
                                )
                            )
                )
            }

            Try.success(t)
                .filterInstanceOf<GraphQLError>()
                .map { gqlError: GraphQLError ->
                    when {
                        gqlError is AbortExecutionException &&
                            gqlError.message?.contains(Regex("(?i)timeout")) ?: false -> {
                            ServerResponse.status(HttpStatus.GATEWAY_TIMEOUT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(errorNodeToErrorsMapConverter(gqlError))
                        }
                        else -> {
                            ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(errorNodeToErrorsMapConverter(gqlError))
                        }
                    }
                }
                .orElseGet { Mono.error(t) }
        }
    }

    private fun convertAnyUnhandledExceptionsIntoServerResponse(
        request: ServerRequest
    ): (Throwable) -> Mono<out ServerResponse> {
        return { err: Throwable ->
            logger.error(
                """handle: [ request.path: ${request.path()} ]: 
                   |uncaught non-platform exception thrown: 
                   |[ type: ${err::class.qualifiedName}, 
                   |message: ${err.message} 
                   |]"""
                    .flatten(),
                err
            )
            ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    mapOf(
                        "errors" to
                            JsonNodeFactory.instance
                                .arrayNode()
                                .add(
                                    mapOf(
                                            "errorType" to err::class.simpleName,
                                            "message" to err.message
                                        )
                                        .asSequence()
                                        .fold(JsonNodeFactory.instance.objectNode()) { on, (k, v) ->
                                            on.put(k, v)
                                        }
                                )
                    )
                )
        }
    }
}
