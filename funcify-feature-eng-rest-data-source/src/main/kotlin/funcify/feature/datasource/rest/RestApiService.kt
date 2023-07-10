package funcify.feature.datasource.rest

import funcify.feature.tools.container.attempt.Try
import java.time.Duration
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer
import org.springframework.web.reactive.function.client.WebClient

/**
 * @author smccarron
 * @created 2/16/22
 */
interface RestApiService {

    val sslTlsSupported: Boolean

    val serviceName: String

    val hostName: String

    val port: UInt

    val serviceContextPath: String

    val timeoutAfter: Duration

    fun getWebClient(): WebClient

    interface Builder {

        fun sslTlsSupported(sslTlsSupported: Boolean): Builder

        fun serviceName(serviceName: String): Builder

        fun hostName(hostName: String): Builder

        fun port(port: UInt): Builder

        fun serviceContextPath(serviceContextPath: String): Builder

        fun serviceSpecificWebClientCustomizer(webClientCustomizer: WebClientCustomizer): Builder

        fun timeoutAfter(elapsedTime: Duration): Builder

        fun build(): Try<RestApiService>
    }
}
