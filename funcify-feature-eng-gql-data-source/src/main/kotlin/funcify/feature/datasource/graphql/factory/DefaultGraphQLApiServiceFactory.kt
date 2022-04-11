package funcify.feature.datasource.graphql.factory

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.graphql.dgs.client.GraphQLResponse
import com.netflix.graphql.dgs.client.MonoGraphQLClient
import funcify.feature.datasource.graphql.GraphQLApiService
import funcify.feature.tools.container.async.Async
import funcify.feature.tools.container.attempt.Try
import io.netty.handler.codec.http.HttpScheme
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientCodecCustomizer
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer
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
internal class DefaultGraphQLApiServiceFactory(private val objectMapper: ObjectMapper,
                                               private val webClientCustomizerProvider: ObjectProvider<WebClientCustomizer>,
                                               private val codecCustomizerProvider: ObjectProvider<WebClientCodecCustomizer>) : GraphQLApiServiceFactory {

    companion object {
        private const val UNSET_SERVICE_NAME: String = ""
        private const val UNSET_HOST_NAME: String = ""
        private const val UNSET_PORT: UInt = 0u
        private const val DEFAULT_GRAPHQL_SERVER_CONTEXT_PATH: String = "/graphql"
    }

    private val webClientCustomizers: List<WebClientCustomizer> by lazy {
        webClientCustomizerProvider.orderedStream()
                .toList()
    }
    private val codecCustomizers: List<WebClientCodecCustomizer> by lazy {
        codecCustomizerProvider.orderedStream()
                .toList()
    }

    private val webClientBuilderUpdater: (WebClient.Builder) -> WebClient.Builder = { wcb: WebClient.Builder ->
        webClientCustomizers.fold(codecCustomizers.fold(wcb) { bldr: WebClient.Builder, wccc: WebClientCodecCustomizer ->
            wccc.customize(bldr)
            bldr
        }) { bldr: WebClient.Builder, wcc: WebClientCustomizer ->
            wcc.customize(bldr)
            bldr
        }
    }


    override fun builder(): GraphQLApiService.Builder {
        return DefaultGraphQLApiServiceBuilder(objectMapper = objectMapper,
                                               webClientUpdater = webClientBuilderUpdater)
    }

    internal class DefaultGraphQLApiServiceBuilder(private val objectMapper: ObjectMapper,
                                                   private val webClientUpdater: (WebClient.Builder) -> WebClient.Builder,
                                                   private var sslTlsSupported: Boolean = true,
                                                   private var serviceName: String = UNSET_SERVICE_NAME,
                                                   private var hostName: String = UNSET_HOST_NAME,
                                                   private var port: UInt = UNSET_PORT,
                                                   private var serviceContextPath: String = DEFAULT_GRAPHQL_SERVER_CONTEXT_PATH) : GraphQLApiService.Builder {

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
                serviceName == UNSET_SERVICE_NAME -> throw  IllegalStateException("service_name has not been set")
                hostName == UNSET_HOST_NAME -> throw IllegalStateException("host_name has not be set")
                else -> {
                    return DefaultGraphQLApiService(sslTlsSupported = sslTlsSupported,
                                                    serviceName = serviceName,
                                                    hostName = hostName,
                                                    port = port,
                                                    serviceContextPath = serviceContextPath,
                                                    objectMapper = objectMapper,
                                                    webClientUpdater = webClientUpdater)
                }
            }
        }

    }

    internal data class DefaultGraphQLApiService(@JsonProperty("ssl_tls_supported")
                                                 override val sslTlsSupported: Boolean,
                                                 @JsonProperty("service_name")
                                                 override val serviceName: String,
                                                 @JsonProperty("host_name")
                                                 override val hostName: String,
                                                 @JsonProperty("port")
                                                 override val port: UInt,
                                                 @JsonProperty("service_context_path")
                                                 override val serviceContextPath: String,
                                                 private val objectMapper: ObjectMapper,
                                                 private val webClientUpdater: (WebClient.Builder) -> WebClient.Builder) : GraphQLApiService {

        private val webClient: WebClient by lazy {
            val scheme: HttpScheme = if (sslTlsSupported) {
                HttpScheme.HTTPS
            } else {
                HttpScheme.HTTP
            }
            val validatedPort: Int = when {
                port == UNSET_PORT && scheme == HttpScheme.HTTPS -> HttpScheme.HTTPS.port()
                port == UNSET_PORT && scheme == HttpScheme.HTTP -> HttpScheme.HTTP.port()
                else -> port.toInt()
            }
            val uriComponentsBuilder: UriComponentsBuilder = UriComponentsBuilder.newInstance()
                    .scheme(scheme.name()
                                    .toString())
                    .host(hostName)
                    .port(validatedPort)
                    .path(serviceContextPath)
            val uriBuilderFactory: UriBuilderFactory = DefaultUriBuilderFactory(uriComponentsBuilder)
            webClientUpdater.invoke(WebClient.builder()
                                            .uriBuilderFactory(uriBuilderFactory))
                    .build()
        }

        private val graphQLClient: MonoGraphQLClient by lazy {
            MonoGraphQLClient.createWithWebClient(webClient)
        }

        override fun executeSingleQuery(query: String,
                                        variables: Map<String, Any>,
                                        operationName: String?): Async<JsonNode> {
            return graphQLClient.reactiveExecuteQuery(query,
                                                      variables,
                                                      operationName)
                    .let { gqlResponseMono: Mono<GraphQLResponse> ->
                        Async.fromMono(gqlResponseMono)
                                .flatMap { gqlResponse: GraphQLResponse ->
                                    Async.fromAttempt(Try.attempt { objectMapper.valueToTree<JsonNode>(gqlResponse.json) })
                                }
                    }
        }

    }


}