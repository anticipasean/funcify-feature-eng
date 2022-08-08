package funcify.feature.datasource.graphql.factory

import arrow.core.foldLeft
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import funcify.feature.datasource.graphql.GraphQLApiService
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse.GRAPHQL_DATA_SOURCE_CREATION_ERROR
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.tools.container.deferred.Deferred
import funcify.feature.tools.extensions.StringExtensions.flatten
import io.netty.handler.codec.http.HttpScheme
import java.util.stream.Collectors
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientCodecCustomizer
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.DefaultUriBuilderFactory
import org.springframework.web.util.UriBuilderFactory
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 4/10/22
 */
internal class DefaultGraphQLApiServiceFactory(
    private val objectMapper: ObjectMapper,
    private val webClientCustomizerProvider: ObjectProvider<WebClientCustomizer>,
    private val codecCustomizerProvider: ObjectProvider<WebClientCodecCustomizer>
) : GraphQLApiServiceFactory {

    companion object {
        private const val UNSET_SERVICE_NAME: String = ""
        private const val UNSET_HOST_NAME: String = ""
        private const val UNSET_PORT: UInt = 0u
        private const val DEFAULT_GRAPHQL_SERVER_CONTEXT_PATH: String = "/graphql"
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
            objectMapper = objectMapper,
            webClientUpdater = webClientBuilderUpdater
        )
    }

    internal class DefaultGraphQLApiServiceBuilder(
        private val objectMapper: ObjectMapper,
        private val webClientUpdater: (WebClient.Builder) -> WebClient.Builder,
        private var sslTlsSupported: Boolean = true,
        private var serviceName: String = UNSET_SERVICE_NAME,
        private var hostName: String = UNSET_HOST_NAME,
        private var port: UInt = UNSET_PORT,
        private var serviceContextPath: String = DEFAULT_GRAPHQL_SERVER_CONTEXT_PATH
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

        override fun build(): GraphQLApiService {
            when {
                serviceName == UNSET_SERVICE_NAME -> {
                    throw GQLDataSourceException(
                        GRAPHQL_DATA_SOURCE_CREATION_ERROR,
                        "service_name has not been set"
                    )
                }
                hostName == UNSET_HOST_NAME -> {
                    throw GQLDataSourceException(
                        GRAPHQL_DATA_SOURCE_CREATION_ERROR,
                        "host_name has not be set"
                    )
                }
                else -> {
                    val httpScheme: HttpScheme =
                        if (sslTlsSupported) {
                            HttpScheme.HTTPS
                        } else {
                            HttpScheme.HTTP
                        }
                    val validatedPort: UInt =
                        when {
                            port == UNSET_PORT && httpScheme == HttpScheme.HTTPS ->
                                HttpScheme.HTTPS.port()
                            port == UNSET_PORT && httpScheme == HttpScheme.HTTP ->
                                HttpScheme.HTTP.port()
                            else -> port.toInt()
                        }.toUInt()
                    return DefaultGraphQLApiService(
                        sslTlsSupported = sslTlsSupported,
                        httpScheme = httpScheme,
                        serviceName = serviceName,
                        hostName = hostName,
                        port = validatedPort,
                        serviceContextPath = serviceContextPath,
                        objectMapper = objectMapper,
                        webClientUpdater = webClientUpdater
                    )
                }
            }
        }
    }

    internal data class DefaultGraphQLApiService(
        @JsonProperty("ssl_tls_supported") override val sslTlsSupported: Boolean,
        @JsonProperty("http_scheme") val httpScheme: HttpScheme,
        @JsonProperty("service_name") override val serviceName: String,
        @JsonProperty("host_name") override val hostName: String,
        @JsonProperty("port") override val port: UInt,
        @JsonProperty("service_context_path") override val serviceContextPath: String,
        private val objectMapper: ObjectMapper,
        private val webClientUpdater: (WebClient.Builder) -> WebClient.Builder
    ) : GraphQLApiService {

        companion object {
            private val logger: Logger =
                LoggerFactory.getLogger(DefaultGraphQLApiService::class.java)
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
            webClientUpdater
                .invoke(WebClient.builder().uriBuilderFactory(uriBuilderFactory))
                .build()
        }

        override fun executeSingleQuery(
            query: String,
            variables: Map<String, Any>,
            operationName: String?
        ): Deferred<JsonNode> {
            logger.debug(
                """execute_single_query: 
                    |[ query.length: ${query.length}, 
                    |variables.size: ${variables.size}, 
                    |operation_name: $operationName ]
                    |""".flatten()
            )
            val queryBodySupplierMono: Mono<ObjectNode> =
                Mono.fromSupplier {
                    mapOf<String, Any?>(
                            "query" to query,
                            "variables" to variables,
                            "operationName" to operationName
                        )
                        .foldLeft(JsonNodeFactory.instance.objectNode()) {
                            objNod: ObjectNode,
                            entry: Map.Entry<String, Any?> ->
                            when (val entVal = entry.value) {
                                is String -> {
                                    objNod.put(entry.key, entVal)
                                }
                                null -> {
                                    objNod.putNull(entry.key)
                                }
                                else -> {
                                    objNod.set(
                                        entry.key,
                                        objectMapper.valueToTree<JsonNode>(entry.value)
                                    )
                                }
                            }
                        }
                }
                    .onErrorResume(IllegalArgumentException::class.java) {
                        e: IllegalArgumentException ->
                        Mono.error(
                            GQLDataSourceException(
                                GQLDataSourceErrorResponse.JSON_CONVERSION_ISSUE,
                                e
                            )
                        )
                    }
            return Deferred.fromMono(
                webClient
                    .post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .acceptCharset(Charsets.UTF_8)
                    .body(queryBodySupplierMono, JsonNode::class.java)
                    .exchangeToMono<JsonNode> { cr: ClientResponse ->
                        if (cr.statusCode().isError) {
                            cr.bodyToMono(String::class.java).flatMap { responseBody: String ->
                                Mono.error<JsonNode>(
                                    GQLDataSourceException(
                                        GQLDataSourceErrorResponse.CLIENT_ERROR,
                                        """
                                        |client_response.status: 
                                        |[ code: ${cr.statusCode().value()}, 
                                        |reason: ${cr.statusCode().reasonPhrase} ] 
                                        |[ body: "$responseBody" ]
                                    """.flatten()
                                    )
                                )
                            }
                        } else {
                            cr.bodyToMono(JsonNode::class.java)
                        }
                    }
            )
        }
    }
}
