package funcify.feature.error

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import org.springframework.http.HttpStatus

/**
 * @author smccarron
 * @created 2022-11-10
 */
abstract class ServiceError(
    /**
     * Direct feedback to caller of the service with server HTTP response code and message
     *
     * Default: 500 INTERNAL_SERVER_ERROR
     *
     * If error occurs as a result of some _downstream failure i.e. failure to connect to upstream
     * server, then a different response type should be used than the default, e.g. BAD_GATEWAY or
     * GATEWAY_TIMEOUT
     */
    open val serverHttpResponse: HttpStatus,
    override val message: String,
    /** Any error being "wrapped" that is not an instance of [ServiceError] */
    override val cause: Throwable?,
    open val serviceErrorHistory: ImmutableList<ServiceError>,
    open val extensions: ImmutableMap<String, Any?>
) : RuntimeException(message, cause), Comparable<ServiceError> {

    companion object {

        /**
         * Should promote to the "front" (i.e. be less than) whichever error might appear as the
         * "cause" or in part lead to the other errors within the set
         */
        private val comparator: Comparator<ServiceError> by lazy {
            Comparator.comparing<ServiceError, HttpStatus>(
                    ServiceError::serverHttpResponse,
                    Comparator.comparing(HttpStatus.INTERNAL_SERVER_ERROR::compareTo)
                        .thenComparing(HttpStatus.SERVICE_UNAVAILABLE::compareTo)
                        .thenComparing(HttpStatus.BAD_GATEWAY::compareTo)
                        .thenComparing(HttpStatus.BAD_REQUEST::compareTo)
                )
                .thenComparing(
                    Comparator.comparing<ServiceError, Boolean> { se: ServiceError ->
                        se.cause == null
                    }
                )
        }

        fun comparator(): Comparator<ServiceError> {
            return comparator
        }

        fun builder(): Builder {
            return DefaultServiceErrorFactory.builder()
        }

        fun of(message: String): ServiceError {
            return builder().message(message).build()
        }

        fun of(message: String, vararg args: Any?): ServiceError {
            return builder().message(message, *args).build()
        }

        fun downstreamServiceUnavailableErrorBuilder(): Builder {
            return builder().serverHttpResponse(HttpStatus.SERVICE_UNAVAILABLE)
        }

        fun downstreamResponseErrorBuilder(): Builder {
            return builder().serverHttpResponse(HttpStatus.BAD_GATEWAY)
        }

        fun downstreamTimeoutErrorBuilder(): Builder {
            return builder().serverHttpResponse(HttpStatus.GATEWAY_TIMEOUT)
        }

        fun invalidRequestErrorBuilder(): Builder {
            return builder().serverHttpResponse(HttpStatus.BAD_REQUEST)
        }
    }

    override fun compareTo(other: ServiceError): Int {
        return comparator().compare(this, other)
    }

    abstract fun update(transformer: Builder.() -> Builder): ServiceError

    abstract fun toJsonNode(): JsonNode

    abstract operator fun plus(other: ServiceError): ServiceError

    interface Builder {

        fun message(message: String): Builder

        fun message(template: String, vararg args: Any?): Builder

        fun cause(throwable: Throwable): Builder

        fun addServiceErrorToHistory(serviceError: ServiceError): Builder

        fun <C : Collection<ServiceError>> addAllServiceErrorsToHistory(errors: C): Builder

        fun removeServiceErrorFromHistoryIf(condition: (ServiceError) -> Boolean): Builder

        fun clearServiceErrorsFromHistory(): Builder

        fun serverHttpResponse(serverHttpResponse: HttpStatus): Builder

        fun putExtension(key: String, value: Any?): Builder

        fun <M : Map<out String, Any?>> putAllExtensions(extensions: M): Builder

        fun removeExtensionIf(condition: (Map.Entry<String, Any?>) -> Boolean): Builder

        fun removeExtension(key: String): Builder

        fun clearExtensions(): Builder

        fun build(): ServiceError
    }
}
