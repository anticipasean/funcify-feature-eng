package funcify.feature.datasource.rest.factory

import arrow.core.compose
import arrow.core.continuations.eagerEffect
import com.fasterxml.jackson.annotation.JsonProperty
import funcify.feature.datasource.rest.RestApiService
import funcify.feature.datasource.rest.RestApiServiceFactory
import funcify.feature.error.ServiceError
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import io.netty.handler.codec.http.HttpScheme
import java.time.Duration
import java.util.stream.Collectors
import org.slf4j.Logger
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientCodecCustomizer
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.DefaultUriBuilderFactory
import org.springframework.web.util.UriBuilderFactory
import org.springframework.web.util.UriComponentsBuilder

internal class DefaultRestApiServiceFactory(
    private val webClientCustomizerProvider: ObjectProvider<WebClientCustomizer>,
    private val codecCustomizerProvider: ObjectProvider<WebClientCodecCustomizer>
) : RestApiServiceFactory {

    companion object {
        private val logger: Logger = loggerFor<DefaultRestApiServiceFactory>()
        private const val UNSET_SERVICE_NAME: String = ""
        private const val UNSET_HOST_NAME: String = ""
        private const val UNSET_PORT: UInt = 0u
        private const val UNSET_SERVICE_CONTEXT_PATH: String = ""
        private const val DEFAULT_REST_API_REQUEST_TIMEOUT_MILLISECONDS: Long = 10000
        private val DEFAULT_REST_API_REQUEST_TIMEOUT: Duration =
            Duration.ofMillis(DEFAULT_REST_API_REQUEST_TIMEOUT_MILLISECONDS)

        internal class DefaultRestApiServiceBuilder(
            private val webClientUpdater: (WebClient.Builder) -> WebClient.Builder,
            private var sslTlsSupported: Boolean = true,
            private var serviceName: String = UNSET_SERVICE_NAME,
            private var hostName: String = UNSET_HOST_NAME,
            private var port: UInt = UNSET_PORT,
            private var serviceContextPath: String = UNSET_SERVICE_CONTEXT_PATH,
            private var serviceSpecificWebClientCustomizers: MutableList<WebClientCustomizer> =
                mutableListOf(),
            private var timeoutAfter: Duration = DEFAULT_REST_API_REQUEST_TIMEOUT
        ) : RestApiService.Builder {

            override fun sslTlsSupported(sslTlsSupported: Boolean): RestApiService.Builder {
                this.sslTlsSupported = sslTlsSupported
                return this
            }

            override fun serviceName(serviceName: String): RestApiService.Builder {
                this.serviceName = serviceName
                return this
            }

            override fun hostName(hostName: String): RestApiService.Builder {
                this.hostName = hostName
                return this
            }

            override fun port(port: UInt): RestApiService.Builder {
                this.port = port
                return this
            }

            override fun serviceContextPath(serviceContextPath: String): RestApiService.Builder {
                this.serviceContextPath = serviceContextPath
                return this
            }

            override fun serviceSpecificWebClientCustomizer(
                webClientCustomizer: WebClientCustomizer
            ): RestApiService.Builder {
                this.serviceSpecificWebClientCustomizers.add(webClientCustomizer)
                return this
            }

            override fun timeoutAfter(elapsedTime: Duration): RestApiService.Builder {
                this.timeoutAfter = elapsedTime
                return this
            }

            override fun build(): Try<RestApiService> {
                return eagerEffect<String, RestApiService> {
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
                        val serviceSpecificWebClientUpdater:
                            (WebClient.Builder) -> WebClient.Builder =
                            serviceSpecificWebClientCustomizers.fold(webClientUpdater) {
                                wcu: (WebClient.Builder) -> WebClient.Builder,
                                serviceSpecificCustomizer: WebClientCustomizer ->
                                wcu.compose<
                                    WebClient.Builder, WebClient.Builder, WebClient.Builder
                                > { wcb: WebClient.Builder ->
                                    serviceSpecificCustomizer.customize(wcb)
                                    wcb
                                }
                            }
                        DefaultRestApiService(
                            sslTlsSupported = sslTlsSupported,
                            httpScheme = httpScheme,
                            serviceName = serviceName,
                            hostName = hostName,
                            port = validatedPort,
                            serviceContextPath = serviceContextPath,
                            timeoutAfter = timeoutAfter,
                            webClientUpdater = serviceSpecificWebClientUpdater
                        )
                    }
                    .fold(
                        { message: String ->
                            Try.failure<RestApiService>(ServiceError.of(message))
                        },
                        { r: RestApiService -> Try.success(r) }
                    )
            }
        }

        internal data class DefaultRestApiService(
            @JsonProperty("ssl_tls_supported") override val sslTlsSupported: Boolean,
            @JsonProperty("http_scheme") val httpScheme: HttpScheme,
            @JsonProperty("service_name") override val serviceName: String,
            @JsonProperty("host_name") override val hostName: String,
            @JsonProperty("port") override val port: UInt,
            @JsonProperty("service_context_path") override val serviceContextPath: String,
            @JsonProperty("timeout_after") override val timeoutAfter: Duration,
            private val webClientUpdater: (WebClient.Builder) -> WebClient.Builder,
        ) : RestApiService {

            companion object {
                private val logger: Logger = loggerFor<DefaultRestApiService>()
            }

            private val backingWebClient: WebClient by lazy {
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
            override fun getWebClient(): WebClient {
                logger.debug("get_web_client: [ service_name: {} ]", serviceName)
                return Try.attempt { backingWebClient }
                    .orElseThrow { t: Throwable ->
                        ServiceError.builder()
                            .message("error occurred when creating backing web client instance")
                            .cause(t)
                            .build()
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

    override fun builder(): RestApiService.Builder {
        return DefaultRestApiServiceBuilder(webClientUpdater = webClientBuilderUpdater)
    }
}
