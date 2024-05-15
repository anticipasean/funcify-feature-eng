package funcify.feature.datasource.graphql.factory

import arrow.core.continuations.eagerEffect
import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.toOption
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import funcify.feature.datasource.graphql.GraphQLApiService
import funcify.feature.datasource.graphql.GraphQLApiServiceFactory
import funcify.feature.error.ServiceError
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.json.JsonMapper
import io.netty.handler.codec.http.HttpScheme
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientCodecCustomizer
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.DefaultUriBuilderFactory
import org.springframework.web.util.UriBuilderFactory
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.util.retry.Retry
import reactor.util.retry.RetryBackoffSpec
import java.time.Duration
import java.util.stream.Collectors

/**
 * @author smccarron
 * @created 4/10/22
 */
internal class DefaultGraphQLApiServiceFactory(
    private val jsonMapper: JsonMapper,
    private val webClientCustomizerProvider: ObjectProvider<WebClientCustomizer>,
    private val codecCustomizerProvider: ObjectProvider<WebClientCodecCustomizer>
) : GraphQLApiServiceFactory {

    companion object {
        private const val UNSET_SERVICE_NAME: String = ""
        private const val UNSET_HOST_NAME: String = ""
        private const val UNSET_PORT: UInt = 0u
        private const val DEFAULT_GRAPHQL_REQUEST_TIMEOUT_MILLISECONDS: Long = 10000
        private val DEFAULT_GRAPHQL_REQUEST_TIMEOUT: Duration =
            Duration.ofMillis(DEFAULT_GRAPHQL_REQUEST_TIMEOUT_MILLISECONDS)
        private const val DEFAULT_GRAPHQL_SERVER_CONTEXT_PATH: String = "/graphql"

        internal class DefaultGraphQLApiServiceBuilder(
            private val jsonMapper: JsonMapper,
            private val webClientUpdater: (WebClient.Builder) -> WebClient.Builder,
            private var sslTlsSupported: Boolean = true,
            private var serviceName: String = UNSET_SERVICE_NAME,
            private var hostName: String = UNSET_HOST_NAME,
            private var port: UInt = UNSET_PORT,
            private var serviceContextPath: String = DEFAULT_GRAPHQL_SERVER_CONTEXT_PATH,
            private var timeoutAfter: Duration = DEFAULT_GRAPHQL_REQUEST_TIMEOUT,
        ) : GraphQLApiService.Builder {

            override fun sslTlsSupported(sslTlsSupported: Boolean): GraphQLApiService.Builder {
                this.sslTlsSupported = sslTlsSupported
                return this
            }

            override fun serviceName(serviceName: String): GraphQLApiService.Builder {
                this.serviceName = serviceName
                return this
            }

            override fun hostName(hostName: String): GraphQLApiService.Builder {
                this.hostName = hostName
                return this
            }

            override fun port(port: UInt): GraphQLApiService.Builder {
                this.port = port
                return this
            }

            override fun serviceContextPath(serviceContextPath: String): GraphQLApiService.Builder {
                this.serviceContextPath = serviceContextPath
                return this
            }

            override fun timeoutAfter(elapsedTime: Duration): GraphQLApiService.Builder {
                this.timeoutAfter = elapsedTime
                return this
            }

            override fun build(): Try<GraphQLApiService> {
                return eagerEffect<String, GraphQLApiService> {
                        ensure(serviceName != UNSET_SERVICE_NAME) {
                            "service_name has not been set"
                        }
                        ensure(hostName != UNSET_HOST_NAME) { "host_name has not be set" }
                        val httpScheme: HttpScheme =
                            if (sslTlsSupported) {
                                HttpScheme.HTTPS
                            } else {
                                HttpScheme.HTTP
                            }
                        val validatedPort: UInt =
                            when {
                                port == UNSET_PORT && httpScheme == HttpScheme.HTTPS -> {
                                    HttpScheme.HTTPS.port()
                                }
                                port == UNSET_PORT && httpScheme == HttpScheme.HTTP -> {
                                    HttpScheme.HTTP.port()
                                }
                                else -> {
                                    port.toInt()
                                }
                            }.toUInt()
                        DefaultGraphQLApiService(
                            sslTlsSupported = sslTlsSupported,
                            httpScheme = httpScheme,
                            serviceName = serviceName,
                            hostName = hostName,
                            port = validatedPort,
                            serviceContextPath = serviceContextPath,
                            timeoutAfter = timeoutAfter,
                            jsonMapper = jsonMapper,
                            webClientUpdater = webClientUpdater
                        )
                    }
                    .fold(
                        { message: String -> Try.failure(ServiceError.of(message)) },
                        { s: GraphQLApiService -> Try.success(s) }
                    )
            }

            internal data class DefaultGraphQLApiService(
                @JsonProperty("ssl_tls_supported") override val sslTlsSupported: Boolean,
                @JsonProperty("http_scheme") private val httpScheme: HttpScheme,
                @JsonProperty("service_name") override val serviceName: String,
                @JsonProperty("host_name") override val hostName: String,
                @JsonProperty("port") override val port: UInt,
                @JsonProperty("service_context_path") override val serviceContextPath: String,
                @JsonProperty("timeout_after") override val timeoutAfter: Duration,
                private val jsonMapper: JsonMapper,
                private val webClientUpdater: (WebClient.Builder) -> WebClient.Builder,
            ) : GraphQLApiService {

                companion object {
                    private val logger: Logger =
                        LoggerFactory.getLogger(DefaultGraphQLApiService::class.java)
                    private const val METHOD_TAG = "execute_single_query"
                }

                private val webClient: WebClient by lazy {
                    val uriComponentsBuilder: UriComponentsBuilder =
                        UriComponentsBuilder.newInstance()
                            .scheme(httpScheme.name().toString())
                            .host(hostName)
                            .port(port.toInt())
                            .path(serviceContextPath)
                    val uriBuilderFactory: UriBuilderFactory =
                        DefaultUriBuilderFactory(uriComponentsBuilder)
                    webClientUpdater(WebClient.builder().uriBuilderFactory(uriBuilderFactory))
                        .build()
                }

                override fun executeSingleQuery(
                    query: String,
                    variables: Map<String, Any?>,
                    operationName: String?
                ): Mono<out JsonNode> {
                    logger.debug(
                        """$METHOD_TAG: 
                        |[ query.length: ${query.length}, 
                        |variables.size: ${variables.size}, 
                        |operation_name: $operationName ]
                        |"""
                            .flatten()
                    )
                    return webClient
                        .post()
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .acceptCharset(Charsets.UTF_8)
                        .body(
                            createGraphQLRequestJson(query, variables, operationName),
                            JsonNode::class.java
                        )
                        .exchangeToMono<JsonNode> { cr: ClientResponse ->
                            if (cr.statusCode().isError) {
                                convertClientResponseIntoDownstreamResponseError(cr)
                            } else {
                                cr.bodyToMono(JsonNode::class.java)
                            }
                        }
                        .retryWhen(createRetrySpec())
                        .publishOn(Schedulers.boundedElastic())
                        .timed()
                        .map { timedJson ->
                            logger.info(
                                "$METHOD_TAG: [ status: successful ][ elapsed_time: {} ms ]",
                                timedJson.elapsed().toMillis()
                            )
                            timedJson.get()
                        }
                        .doOnError { t: Throwable ->
                            logger.error(
                                "$METHOD_TAG: [ status: failed ][ type: {}, message: {} ]",
                                t::class.simpleName,
                                t.message
                            )
                        }
                }

                private fun createGraphQLRequestJson(
                    query: String,
                    variables: Map<String, Any?>,
                    operationName: String?,
                ): Mono<out JsonNode> {
                    return jsonMapper
                        .fromKotlinObject(
                            mapOf<String, Any?>(
                                "query" to query,
                                "variables" to variables,
                                "operationName" to operationName
                            )
                        )
                        .toJsonNode()
                        .mapFailure { t: Throwable ->
                            ServiceError.builder()
                                .message("error converting variables into JSON")
                                .cause(t)
                                .build()
                        }
                        .toMono()
                        .cache()
                }

                private fun convertClientResponseIntoDownstreamResponseError(
                    clientResponse: ClientResponse
                ): Mono<JsonNode> {
                    return when (clientResponse.headers().contentType().orElse(null)) {
                        MediaType.APPLICATION_JSON,
                        MediaType.APPLICATION_GRAPHQL_RESPONSE -> {
                            clientResponse.bodyToMono(JsonNode::class.java).flatMap {
                                responseBody: JsonNode ->
                                Mono.error<JsonNode> {
                                    createDownstreamResponseError(clientResponse, responseBody)
                                }
                            }
                        }
                        else -> {
                            clientResponse.bodyToMono(String::class.java).flatMap {
                                responseBody: String ->
                                Mono.error<JsonNode> {
                                    createDownstreamResponseError(clientResponse, responseBody)
                                }
                            }
                        }
                    }
                }

                private fun createDownstreamResponseError(
                    clientResponse: ClientResponse,
                    responseBody: Any?,
                ): Throwable {
                    return when (HttpStatus.valueOf(clientResponse.statusCode().value())) {
                        HttpStatus.SERVICE_UNAVAILABLE -> {
                            ServiceError.downstreamServiceUnavailableErrorBuilder()
                                .message(
                                    createDownstreamResponseErrorMessage(
                                        clientResponse,
                                        responseBody
                                    )
                                )
                                .build()
                        }
                        HttpStatus.GATEWAY_TIMEOUT -> {
                            ServiceError.downstreamTimeoutErrorBuilder()
                                .message(
                                    createDownstreamResponseErrorMessage(
                                        clientResponse,
                                        responseBody
                                    )
                                )
                                .build()
                        }
                        else -> {
                            ServiceError.downstreamResponseErrorBuilder()
                                .message(
                                    createDownstreamResponseErrorMessage(
                                        clientResponse,
                                        responseBody
                                    )
                                )
                                .build()
                        }
                    }
                }

                private fun createDownstreamResponseErrorMessage(
                    clientResponse: ClientResponse,
                    responseBody: Any?
                ): String {
                    return sequenceOf(
                            "code" to clientResponse.statusCode(),
                            "reason" to
                                HttpStatus.valueOf(clientResponse.statusCode().value())
                                    .reasonPhrase,
                            "body" to responseBody
                        )
                        .fold(JsonNodeFactory.instance.objectNode()) { on, (k, v) ->
                            when (v) {
                                is JsonNode -> {
                                    on.set(k, v)
                                }
                                is String -> {
                                    on.put(k, v)
                                }
                                else -> {
                                    on.put(k, v.toString())
                                }
                            }
                        }
                        .let { on: ObjectNode ->
                            JsonNodeFactory.instance
                                .objectNode()
                                .set<ObjectNode>("error_response", on)
                        }
                        .toString()
                }

                private fun createRetrySpec(): Retry {
                    return Retry.backoff(3, Duration.ofMillis(50))
                        .doAfterRetry { rs: Retry.RetrySignal ->
                            logger.warn(
                                """{}: 
                                |[ status: last request failed ]
                                |[ retry_count: {} ]
                                |[ last_error: { type: {}, message: {} } ]"""
                                    .flatten(),
                                METHOD_TAG,
                                rs.totalRetries(),
                                rs.failure()
                                    .toOption()
                                    .filterIsInstance<ServiceError>()
                                    .and(ServiceError::class.simpleName.toOption())
                                    .getOrElse { rs.failure()::class.qualifiedName },
                                rs.failure()
                                    .toOption()
                                    .filterIsInstance<ServiceError>()
                                    .map(ServiceError::toJsonNode)
                                    .getOrElse { rs.failure().message },
                            )
                        }
                        .onRetryExhaustedThrow { rbt: RetryBackoffSpec, rs: Retry.RetrySignal ->
                            logger.warn(
                                "{}: [ status: retries exhausted ][ total_retries: {} ]",
                                METHOD_TAG,
                                rs.totalRetries()
                            )
                            rs.failure()
                        }
                }
            }
        }
    }

    private val webClientCustomizers: List<WebClientCustomizer> by lazy {
        webClientCustomizerProvider.orderedStream().collect(Collectors.toList())
    }
    private val codecCustomizers: List<WebClientCodecCustomizer> by lazy {
        codecCustomizerProvider.orderedStream().collect(Collectors.toList())
    }

    private val webClientBuilderUpdater: (WebClient.Builder) -> WebClient.Builder =
        { wcb: WebClient.Builder ->
            webClientCustomizers.fold(
                codecCustomizers.fold(wcb) { bldr: WebClient.Builder, wccc: WebClientCodecCustomizer
                    ->
                    wccc.customize(bldr)
                    bldr
                }
            ) { bldr: WebClient.Builder, wcc: WebClientCustomizer ->
                wcc.customize(bldr)
                bldr
            }
        }

    override fun builder(): GraphQLApiService.Builder {
        return DefaultGraphQLApiServiceBuilder(
            jsonMapper = jsonMapper,
            webClientUpdater = webClientBuilderUpdater
        )
    }
}
