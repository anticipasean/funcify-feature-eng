package funcify.feature.datasource.rest.factory

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import funcify.feature.datasource.rest.RestApiService
import funcify.feature.datasource.rest.error.RestApiDataSourceException
import funcify.feature.datasource.rest.error.RestApiErrorResponse
import funcify.feature.datasource.rest.error.RestApiErrorResponse.REST_API_DATA_SOURCE_CREATION_ERROR
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import io.netty.handler.codec.http.HttpScheme
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
    private val objectMapper: ObjectMapper,
    private val webClientCustomizerProvider: ObjectProvider<WebClientCustomizer>,
    private val codecCustomizerProvider: ObjectProvider<WebClientCodecCustomizer>
) : RestApiServiceFactory {

    companion object {
        private val logger: Logger = loggerFor<DefaultRestApiServiceFactory>()
        private const val UNSET_SERVICE_NAME: String = ""
        private const val UNSET_HOST_NAME: String = ""
        private const val UNSET_PORT: UInt = 0u
        private const val UNSET_SERVICE_CONTEXT_PATH: String = ""

        internal class DefaultRestApiServiceBuilder(
            private val objectMapper: ObjectMapper,
            private val webClientUpdater: (WebClient.Builder) -> WebClient.Builder,
            private var sslTlsSupported: Boolean = true,
            private var serviceName: String = UNSET_SERVICE_NAME,
            private var hostName: String = UNSET_HOST_NAME,
            private var port: UInt = UNSET_PORT,
            private var serviceContextPath: String = UNSET_SERVICE_CONTEXT_PATH
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

            override fun build(): RestApiService {
                when {
                    serviceName == UNSET_SERVICE_NAME -> {
                        throw RestApiDataSourceException(
                            REST_API_DATA_SOURCE_CREATION_ERROR,
                            "service_name has not been set"
                        )
                    }
                    hostName == UNSET_HOST_NAME -> {
                        throw RestApiDataSourceException(
                            REST_API_DATA_SOURCE_CREATION_ERROR,
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
                        val validatedPort =
                            when {
                                port == UNSET_PORT && httpScheme == HttpScheme.HTTPS ->
                                    HttpScheme.HTTPS.port()
                                port == UNSET_PORT && httpScheme == HttpScheme.HTTP ->
                                    HttpScheme.HTTP.port()
                                else -> port.toInt()
                            }.toUInt()
                        return DefaultRestApiService(
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

        internal data class DefaultRestApiService(
            @JsonProperty("ssl_tls_supported") override val sslTlsSupported: Boolean,
            @JsonProperty("http_scheme") val httpScheme: HttpScheme,
            @JsonProperty("service_name") override val serviceName: String,
            @JsonProperty("host_name") override val hostName: String,
            @JsonProperty("port") override val port: UInt,
            @JsonProperty("service_context_path") override val serviceContextPath: String,
            val objectMapper: ObjectMapper,
            val webClientUpdater: (WebClient.Builder) -> WebClient.Builder
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
                logger.debug("get_web_client: [ ]")
                return Try.attempt { backingWebClient }.orElseThrow { t: Throwable ->
                    RestApiDataSourceException(
                        RestApiErrorResponse.UNEXPECTED_ERROR,
                        "error occurred when creating backing web client instance",
                        t
                    )
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
        return DefaultRestApiServiceBuilder(
            objectMapper = objectMapper,
            webClientUpdater = webClientBuilderUpdater
        )
    }
}
