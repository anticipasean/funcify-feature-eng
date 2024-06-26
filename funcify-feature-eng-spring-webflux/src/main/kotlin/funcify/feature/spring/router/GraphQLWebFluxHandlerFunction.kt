package funcify.feature.spring.router

import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.JsonNodeType
import funcify.feature.error.ServiceError
import funcify.feature.materializer.executor.GraphQLSingleRequestExecutor
import funcify.feature.materializer.request.GraphQLExecutionInputCustomizer
import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.request.factory.RawGraphQLRequestFactory
import funcify.feature.materializer.response.SerializedGraphQLResponse
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.attempt.Try.Companion.flatMapFailure
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.MonoExtensions.filter
import funcify.feature.tools.extensions.PersistentMapExtensions.reduceEntriesToPersistentMap
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.ThrowableExtensions.possiblyNestedHeadStackTraceElement
import funcify.feature.tools.json.JsonMapper
import graphql.ExecutionResult
import graphql.GraphQLError
import graphql.execution.AbortExecutionException
import graphql.language.SourceLocation
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
        private const val METHOD_TAG = "handle"
        private const val QUERY_KEY = "query"
        private const val GRAPHQL_REQUEST_VARIABLES_KEY = "variables"
        private const val DATA_KEY = "data"
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
        logger.info("$METHOD_TAG: [ request.path: ${request.path()} ]")
        return Mono.defer { convertServerRequestIntoRawGraphQLRequest(request) }
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap { rgr: RawGraphQLRequest ->
                graphQLSingleRequestExecutor.executeSingleRequest(rgr)
            }
            .flatMap(convertAggregateSerializedGraphQLResponseIntoServerResponse())
            .doOnError(logAnyErrorsBeforeCreatingServerResponse())
            .onErrorResume(ServiceError::class.java, convertCommonExceptionTypeIntoServerResponse())
            .onErrorResume(
                { t: Throwable -> t is GraphQLError },
                convertGraphQLErrorTypeIntoServerResponse()
            )
            .onErrorResume(convertAnyUnhandledExceptionsIntoServerResponse())
            .timed()
            .map { timedResponse: Timed<ServerResponse> ->
                val successOrFailureStatus: String =
                    if (timedResponse.get().statusCode().is2xxSuccessful) {
                        "success"
                    } else {
                        "failure"
                    }
                logger.info(
                    "$METHOD_TAG: [ status: {} ][ elapsed_time: {} ms, response_http_status: {} ]",
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
        logger.info(
            """$METHOD_TAG: [ convert server_request into raw_graphql_request ]
                |[ server_request.headers.content-type: {} ]"""
                .flatten(),
            request.headers().contentType().orElse(null)
        )
        return when (request.headers().contentType().orElse(null)) {
            MediaType.TEXT_PLAIN -> {
                transformRawGraphQLOperationTextIntoRawGraphQLRequest(request)
            }
            MediaType.APPLICATION_JSON -> {
                transformJsonIntoRawGraphQLRequest(request)
            }
            // TODO: Add support for persisted queries wherein Apollo-style hash has been submitted
            // in lieu of query text
            else -> {
                // Assume when no content-type has been provided that content-type is
                // [MediaType.APPLICATION_JSON]
                transformJsonIntoRawGraphQLRequest(request)
            }
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

    private fun transformJsonIntoRawGraphQLRequest(
        request: ServerRequest
    ): Mono<out RawGraphQLRequest> {
        return request
            .bodyToMono<JsonNode?>(JsonNode::class.java)
            .flatMap { nullableQueryJson: JsonNode? ->
                nullableQueryJson.toMono("json_node_form_of_input")
            }
            .filter(JsonNode::isObject) { jn: JsonNode ->
                ServiceError.invalidRequestErrorBuilder()
                    .message(
                        """request.body does not match expected JSON structure; 
                        |[ expected_type: %s, actual_type: %s ]"""
                            .flatten(),
                        JsonNodeType.OBJECT,
                        jn.nodeType
                    )
                    .build()
            }
            .map { queryJson: JsonNode ->
                rawGraphQLRequestFactory
                    .builder()
                    .uri(request.uri())
                    .headers(extractMessageHeadersFromRequest(request))
                    .operationName(queryJson.path(OPERATION_NAME_KEY).asText(""))
                    .principalPublisher(request.principal())
                    .rawGraphQLQueryText(queryJson.path(QUERY_KEY).asText(""))
                    .variables(extractGraphQLVariablesFromJson(queryJson))
                    .locale(extractLocaleFromRequest(request))
                    .expectedOutputFieldNames(
                        queryJson
                            .path(OUTPUT_KEY)
                            .toOption()
                            .filterIsInstance<ArrayNode>()
                            .map(ArrayNode::asSequence)
                            .getOrElse(::emptySequence)
                            .mapNotNull(JsonNode::textValue)
                            .filterNot(String::isBlank)
                            .toList()
                    )
                    .executionInputCustomizers(graphQLExecutionInputCustomizers)
                    .build()
            }
    }

    private fun extractGraphQLVariablesFromJson(jsonNode: JsonNode): Map<String, Any?> {
        return jsonNode
            .path(GRAPHQL_REQUEST_VARIABLES_KEY)
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

    private fun convertAggregateSerializedGraphQLResponseIntoServerResponse():
        (SerializedGraphQLResponse) -> Mono<out ServerResponse> {
        return { response: SerializedGraphQLResponse ->
            logger.info(
                "$METHOD_TAG: [ converting serialized response into server_response ][ {} ]",
                response.executionResult.errors
                    .toOption()
                    .map(List<GraphQLError>::asSequence)
                    .getOrElse(::emptySequence)
                    .map { ge: GraphQLError -> convertGraphQLErrorIntoErrorJsonNode(ge) }
                    .withIndex()
                    .joinToString("; ", "error(s): [ ", " ]") { (idx: Int, jn: JsonNode) ->
                        "[$idx]: $jn"
                    }
            )
            when {
                response.resultAsColumnarJsonObject.isDefined() -> {
                    ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(
                            mapOf(
                                OUTPUT_KEY to
                                    response.resultAsColumnarJsonObject.getOrElse {
                                        JsonNodeFactory.instance.objectNode()
                                    },
                                ERRORS_KEY to
                                    convertExecutionResultErrorsBlockIntoJSON(
                                        response.executionResult
                                    )
                            )
                        )
                }
                else -> {
                    ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(
                            Try.attempt { response.executionResult.toSpecification() }
                                .orElseGet {
                                    createAlternativeNonTabularExecutionResultJSONRepresentation(
                                        response.executionResult
                                    )
                                }
                        )
                }
            }
        }
    }

    private fun createAlternativeNonTabularExecutionResultJSONRepresentation(
        executionResult: ExecutionResult
    ): Map<String, Any?> {
        return when {
            executionResult.isDataPresent -> {
                mapOf(
                    DATA_KEY to executionResult.getData(),
                    ERRORS_KEY to convertExecutionResultErrorsBlockIntoJSON(executionResult)
                )
            }
            else -> {
                mapOf(ERRORS_KEY to convertExecutionResultErrorsBlockIntoJSON(executionResult))
            }
        }
    }

    private fun convertExecutionResultErrorsBlockIntoJSON(
        executionResult: ExecutionResult
    ): ArrayNode {
        return executionResult.errors
            .toOption()
            .getOrElse(::emptyList)
            .asSequence()
            .map { ge: GraphQLError -> convertGraphQLErrorIntoErrorJsonNode(ge) }
            .fold(JsonNodeFactory.instance.arrayNode(), ArrayNode::add)
    }

    private fun logAnyErrorsBeforeCreatingServerResponse(): (Throwable) -> Unit {
        return { t: Throwable ->
            when (t) {
                is ServiceError -> {
                    logger.error(
                        """$METHOD_TAG: [ converting error(s) into server_response ]
                            |[ error(s) occurred ][ type: {}, json: {} ]"""
                            .flatten(),
                        ServiceError::class.simpleName,
                        t.toJsonNode()
                    )
                }
                else -> {
                    logger.error(
                        """$METHOD_TAG: [ converting error(s) into server_response ]
                            |[ type: {}, message: {}, stack_trace_element[0]: {} ]"""
                            .flatten(),
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
                .bodyValue(mapOf(ERRORS_KEY to listOf(serviceError.toJsonNode())))
        }
    }

    private fun convertGraphQLErrorIntoErrorJsonNode(graphQLError: GraphQLError): JsonNode {
        return Try.attempt { graphQLError.toSpecification() }
            .map(removeCauseFromSpecIfPresentLoggingWarning())
            .flatMap { spec: Map<String, Any?> -> jsonMapper.fromKotlinObject(spec).toJsonNode() }
            .flatMapFailure { _: Throwable ->
                if (
                    graphQLError.locations
                        .toOption()
                        .map(List<SourceLocation?>::asSequence)
                        .getOrElse(::emptySequence)
                        .any { sl: SourceLocation? -> sl == null }
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
                    jsonMapper
                        .fromKotlinObject(
                            mapOf(
                                "errorType" to graphQLError.errorType,
                                "message" to graphQLError.message
                            )
                        )
                        .toJsonNode()
                }
            }
            .orElseThrow()
    }

    private fun removeCauseFromSpecIfPresentLoggingWarning():
        (Map<String, Any?>) -> Map<String, Any?> {
        return { spec ->
            if (spec.containsKey("exception")) {
                val causeOpt: Option<Throwable> =
                    spec.get("exception").toOption().filterIsInstance<Throwable>()
                logger.warn(
                    """
                    |${METHOD_TAG}: [ converting graphql_error into json ] 
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
            val errorNodeToErrorsMapConverter: (GraphQLError) -> Map<String, Any?> =
                { ge: GraphQLError ->
                    mapOf(ERRORS_KEY to convertGraphQLErrorIntoErrorJsonNode(ge))
                }
            when {
                t is AbortExecutionException &&
                    t.message?.contains(Regex("(?i)timeout")) == true -> {
                    ServerResponse.status(HttpStatus.GATEWAY_TIMEOUT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(errorNodeToErrorsMapConverter(t))
                }
                t is GraphQLError -> {
                    ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(errorNodeToErrorsMapConverter(t))
                }
                else -> {
                    Mono.error(t)
                }
            }
        }
    }

    private fun convertAnyUnhandledExceptionsIntoServerResponse():
        (Throwable) -> Mono<out ServerResponse> {
        return { t: Throwable ->
            logger.error(
                """$METHOD_TAG: [ converting unhandled exception into server_response ]
                   |[ uncaught non-platform exception thrown: 
                   |[ type: ${t::class.qualifiedName}, 
                   |message: ${t.message} ] 
                   |]"""
                    .flatten(),
                t
            )
            ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    mapOf(
                        ERRORS_KEY to
                            listOf(
                                mapOf("errorType" to t::class.simpleName, "message" to t.message)
                            )
                    )
                )
        }
    }
}
