package funcify.feature.error

import arrow.core.foldLeft
import arrow.core.getOrElse
import arrow.core.toOption
import arrow.typeclasses.Monoid
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import funcify.feature.error.DefaultServiceErrorFactory.DefaultServiceErrorMonoid.combine
import funcify.feature.tools.extensions.ThrowableExtensions.possiblyNestedHeadStackTraceElement
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.springframework.http.HttpStatus

/**
 * @author smccarron
 * @created 2022-11-11
 */
internal object DefaultServiceErrorFactory : ServiceErrorFactory {

    private const val DEFAULT_ERROR_MESSAGE = "errors occurred"
    private val DEFAULT_SERVER_HTTP_RESPONSE = HttpStatus.INTERNAL_SERVER_ERROR
    private val DEFAULT_SERVICE_ERROR by lazy {
        DefaultServiceError(
            serverHttpResponse = DEFAULT_SERVER_HTTP_RESPONSE,
            message = DEFAULT_ERROR_MESSAGE,
            cause = null,
            serviceErrorHistory = persistentListOf(),
            extensions = persistentMapOf()
        )
    }

    private class DefaultServiceErrorBuilder(
        private var serverHttpResponse: HttpStatus = DEFAULT_SERVER_HTTP_RESPONSE,
        private var message: String = DEFAULT_ERROR_MESSAGE,
        private var cause: Throwable? = null,
        private val serviceErrorHistory: PersistentList.Builder<ServiceError> =
            persistentListOf<ServiceError>().builder(),
        private val extensions: PersistentMap.Builder<String, Any?> =
            persistentMapOf<String, Any?>().builder(),
    ) : ServiceError.Builder {

        override fun message(message: String): ServiceError.Builder {
            this.message = message
            return this
        }

        override fun message(template: String, vararg args: Any?): ServiceError.Builder {
            this.message = String.format(template, *args)
            return this
        }

        override fun cause(throwable: Throwable?): ServiceError.Builder {
            var t: Throwable? = throwable
            while (t?.cause != null) {
                t = t.cause
            }
            this.cause = t
            return this
        }

        override fun addServiceErrorToHistory(serviceError: ServiceError): ServiceError.Builder {
            this.serviceErrorHistory.add(serviceError)
            return this
        }

        override fun <C : Collection<ServiceError>> addAllServiceErrorsToHistory(
            errors: C
        ): ServiceError.Builder {
            this.serviceErrorHistory.addAll(errors)
            return this
        }

        override fun removeServiceErrorFromHistoryIf(
            condition: (ServiceError) -> Boolean
        ): ServiceError.Builder {
            this.serviceErrorHistory.removeIf(condition)
            return this
        }

        override fun clearServiceErrorsFromHistory(): ServiceError.Builder {
            this.serviceErrorHistory.clear()
            return this
        }

        override fun serverHttpResponse(serverHttpResponse: HttpStatus): ServiceError.Builder {
            this.serverHttpResponse = serverHttpResponse
            return this
        }

        override fun putExtension(key: String, value: Any?): ServiceError.Builder {
            this.extensions[key] = value
            return this
        }

        override fun <M : Map<out String, Any?>> putAllExtensions(
            extensions: M
        ): ServiceError.Builder {
            this.extensions.putAll(extensions)
            return this
        }

        override fun removeExtensionIf(
            condition: (Map.Entry<String, Any?>) -> Boolean
        ): ServiceError.Builder {
            this.extensions.filter(condition).forEach { (k, _) -> this.extensions.remove(k) }
            return this
        }

        override fun removeExtension(key: String): ServiceError.Builder {
            this.extensions.remove(key)
            return this
        }

        override fun clearExtensions(): ServiceError.Builder {
            this.extensions.clear()
            return this
        }

        override fun build(): ServiceError {
            if (!message.contains(Regex(" with cause ")) && cause != null) {
                val c: Throwable = cause!!
                message = buildString {
                    append(message)
                    append(" with cause ")
                    append(
                        mapOf(
                                "type" to c::class.simpleName,
                                "message" to c.message,
                                "stacktrace[0]" to c.possiblyNestedHeadStackTraceElement()
                            )
                            .asSequence()
                            .joinToString(", ", "[ ", " ]") { (k, v) -> "$k: $v" }
                    )
                }
            }
            return DefaultServiceError(
                serverHttpResponse = serverHttpResponse,
                message = message,
                cause = cause,
                serviceErrorHistory = serviceErrorHistory.build(),
                extensions = extensions.build()
            )
        }
    }

    private data class DefaultServiceError(
        override val serverHttpResponse: HttpStatus,
        override val message: String,
        override val cause: Throwable?,
        override val serviceErrorHistory: PersistentList<ServiceError>,
        override val extensions: PersistentMap<String, Any?>
    ) : ServiceError(serverHttpResponse, message, cause, serviceErrorHistory, extensions) {

        private val jsonNodeForm: JsonNode by lazy {
            val serverHttpResponseJson: ObjectNode =
                mapOf(
                        "code" to JsonNodeFactory.instance.numberNode(serverHttpResponse.value()),
                        "reason" to
                            JsonNodeFactory.instance.textNode(serverHttpResponse.reasonPhrase)
                    )
                    .foldLeft(JsonNodeFactory.instance.objectNode()) { on, (k, v) -> on.set(k, v) }
            val causeJson: JsonNode =
                if (cause == null) {
                    JsonNodeFactory.instance.nullNode()
                } else {
                    mapOf(
                            "type" to
                                cause
                                    .toOption()
                                    .mapNotNull { t -> t::class.qualifiedName }
                                    .getOrElse { "<NA>" },
                            "message" to
                                cause.toOption().mapNotNull { t -> t.message }.getOrElse { "<NA>" },
                            "stacktrace[0]" to
                                cause
                                    .toOption()
                                    .mapNotNull { t -> t.stackTrace }
                                    .filter { st: Array<StackTraceElement> -> st.isNotEmpty() }
                                    .mapNotNull { st -> st[0] }
                                    .map { ste: StackTraceElement -> ste.toString() }
                                    .getOrElse { "<NA>" }
                        )
                        .foldLeft(JsonNodeFactory.instance.objectNode()) { on, (k, v) ->
                            on.put(k, v)
                        }
                }
            /*
             * Exclude extensions from json_node since extensions may not be serializable into JSON
             */
            mapOf(
                    "server_http_response" to serverHttpResponseJson,
                    "message" to JsonNodeFactory.instance.textNode(message),
                    "cause" to causeJson,
                    "service_error_history" to
                        serviceErrorHistory.fold(
                            JsonNodeFactory.instance.arrayNode(serviceErrorHistory.size)
                        ) { an, se ->
                            an.add(se.toJsonNode())
                        },
                )
                .foldLeft(JsonNodeFactory.instance.objectNode()) { on, (k, v) -> on.set(k, v) }
        }

        override fun update(transformer: Builder.() -> Builder): ServiceError {
            return transformer(
                    DefaultServiceErrorBuilder(
                        serverHttpResponse = this.serverHttpResponse,
                        message = this.message,
                        cause = this.cause,
                        serviceErrorHistory = this.serviceErrorHistory.builder(),
                        extensions = this.extensions.builder()
                    )
                )
                .build()
        }

        override fun toJsonNode(): JsonNode {
            return jsonNodeForm
        }

        override fun plus(other: ServiceError): ServiceError {
            return this.combine(other)
        }
    }

    internal object DefaultServiceErrorMonoid : Monoid<ServiceError> {

        override fun empty(): ServiceError {
            return DEFAULT_SERVICE_ERROR
        }

        override fun ServiceError.combine(b: ServiceError): ServiceError {
            val a: ServiceError = this
            return when {
                a == DEFAULT_SERVICE_ERROR || b == DEFAULT_SERVICE_ERROR -> {
                    when (a) {
                        DEFAULT_SERVICE_ERROR -> {
                            b
                        }
                        else -> {
                            a
                        }
                    }
                }
                a > b -> {
                    b.update {
                        addServiceErrorToHistory(a.update { clearServiceErrorsFromHistory() })
                            .addAllServiceErrorsToHistory(a.serviceErrorHistory)
                    }
                }
                else -> {
                    a.update {
                        addServiceErrorToHistory(b.update { clearServiceErrorsFromHistory() })
                            .addAllServiceErrorsToHistory(b.serviceErrorHistory)
                    }
                }
            }
        }
    }

    override fun builder(): ServiceError.Builder {
        return DefaultServiceErrorBuilder()
    }
}
