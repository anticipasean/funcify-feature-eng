package funcify.feature.datasource.rest

import org.springframework.web.reactive.function.client.WebClient

/**
 *
 * @author smccarron
 * @created 2/16/22
 */
interface RestApiService {

    val sslTlsSupported: Boolean

    val serviceName: String

    val hostName: String

    val port: UInt

    val serviceContextPath: String

    fun getWebClient(): WebClient

    interface Builder {

        fun sslTlsSupported(sslTlsSupported: Boolean): Builder

        fun serviceName(serviceName: String): Builder

        fun hostName(hostName: String): Builder

        fun port(port: UInt): Builder

        fun serviceContextPath(serviceContextPath: String): Builder

        fun build(): RestApiService
    }
}
