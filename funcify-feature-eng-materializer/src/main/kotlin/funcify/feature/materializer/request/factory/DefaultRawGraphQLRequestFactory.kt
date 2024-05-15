package funcify.feature.materializer.request.factory

import funcify.feature.error.ServiceError
import funcify.feature.materializer.request.DefaultRawGraphQLRequest
import funcify.feature.materializer.request.GraphQLExecutionInputCustomizer
import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.PersistentMapExtensions.toPersistentMap
import graphql.execution.ExecutionId
import java.net.URI
import java.security.Principal
import java.util.*
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import org.slf4j.Logger
import org.springframework.messaging.MessageHeaders
import reactor.core.publisher.Mono

internal class DefaultRawGraphQLRequestFactory : RawGraphQLRequestFactory {

    companion object {
        private val logger: Logger = loggerFor<DefaultRawGraphQLRequestFactory>()
        private val UNSET_REQUEST_ID: UUID = UUID(0, 0)
        private val UNSET_EXECUTION_ID: ExecutionId = ExecutionId.from(UNSET_REQUEST_ID.toString())
        private val UNSET_URI: URI = URI.create("http://localhost")
        private const val UNSET_RAW_GRAPHQL_QUERY_TEXT: String = ""
        private const val UNSET_OPERATION_NAME: String = ""

        internal class DefaultRawGraphQLRequestBuilder(
            private val existingRawGraphQLRequest: DefaultRawGraphQLRequest? = null,
            private var requestId: UUID = existingRawGraphQLRequest?.requestId ?: UNSET_REQUEST_ID,
            private var executionId: ExecutionId =
                existingRawGraphQLRequest?.executionId ?: UNSET_EXECUTION_ID,
            private var uri: URI = existingRawGraphQLRequest?.uri ?: UNSET_URI,
            private var headers: MessageHeaders =
                existingRawGraphQLRequest?.headers ?: MessageHeaders(mapOf()),
            private var principalPublisher: Mono<out Principal> =
                existingRawGraphQLRequest?.principalPublisher ?: Mono.empty(),
            private var rawGraphQLQueryText: String =
                existingRawGraphQLRequest?.rawGraphQLQueryText ?: UNSET_RAW_GRAPHQL_QUERY_TEXT,
            private var operationName: String =
                existingRawGraphQLRequest?.operationName ?: UNSET_OPERATION_NAME,
            private var variables: MutableMap<String, Any?> =
                existingRawGraphQLRequest?.variables?.builder()
                    ?: persistentMapOf<String, Any?>().builder(),
            private var locale: Locale = existingRawGraphQLRequest?.locale ?: Locale.getDefault(),
            private var expectedOutputFieldNames: MutableList<String> =
                existingRawGraphQLRequest?.expectedOutputFieldNames?.builder()
                    ?: persistentListOf<String>().builder(),
            private var executionInputCustomizers: MutableList<GraphQLExecutionInputCustomizer> =
                existingRawGraphQLRequest?.executionInputCustomizers?.builder()
                    ?: persistentListOf<GraphQLExecutionInputCustomizer>().builder()
        ) : RawGraphQLRequest.Builder {

            override fun requestId(requestId: UUID): RawGraphQLRequest.Builder {
                this.requestId = requestId
                return this
            }

            override fun executionId(executionId: ExecutionId): RawGraphQLRequest.Builder {
                this.executionId = executionId
                return this
            }

            override fun uri(uri: URI): RawGraphQLRequest.Builder {
                this.uri = uri
                return this
            }

            override fun headers(headers: MessageHeaders): RawGraphQLRequest.Builder {
                this.headers = headers
                return this
            }

            override fun principalPublisher(
                principalPublisher: Mono<out Principal>
            ): RawGraphQLRequest.Builder {
                this.principalPublisher = principalPublisher
                return this
            }

            override fun rawGraphQLQueryText(
                rawGraphQLQueryText: String
            ): RawGraphQLRequest.Builder {
                this.rawGraphQLQueryText = rawGraphQLQueryText
                return this
            }

            override fun operationName(operationName: String): RawGraphQLRequest.Builder {
                this.operationName = operationName
                return this
            }

            override fun variables(variables: Map<String, Any?>): RawGraphQLRequest.Builder {
                this.variables.putAll(variables)
                return this
            }

            override fun variable(key: String, value: Any?): RawGraphQLRequest.Builder {
                this.variables[key] = value
                return this
            }

            override fun removeVariable(key: String): RawGraphQLRequest.Builder {
                if (this.variables.containsKey(key)) {
                    this.variables.remove(key)
                }
                return this
            }

            override fun removeVariableIf(
                condition: (Map.Entry<String, Any?>) -> Boolean
            ): RawGraphQLRequest.Builder {
                this.variables =
                    this.variables.asSequence().filterNot(condition).toPersistentMap().builder()
                return this
            }

            override fun clearVariables(): RawGraphQLRequest.Builder {
                this.variables.clear()
                return this
            }

            override fun locale(locale: Locale): RawGraphQLRequest.Builder {
                this.locale = locale
                return this
            }

            override fun expectedOutputFieldNames(
                expectedOutputFieldNames: List<String>
            ): RawGraphQLRequest.Builder {
                if (this.expectedOutputFieldNames.isEmpty()) {
                    this.expectedOutputFieldNames =
                        expectedOutputFieldNames.toPersistentList().builder()
                } else {
                    this.expectedOutputFieldNames.addAll(expectedOutputFieldNames)
                }
                return this
            }

            override fun expectedOutputFieldName(
                expectedOutputFieldName: String
            ): RawGraphQLRequest.Builder {
                this.expectedOutputFieldNames.add(expectedOutputFieldName)
                return this
            }

            override fun executionInputCustomizer(
                executionInputCustomizer: GraphQLExecutionInputCustomizer
            ): RawGraphQLRequest.Builder {
                this.executionInputCustomizers.add(executionInputCustomizer)
                return this
            }

            override fun executionInputCustomizers(
                executionInputCustomizers: List<GraphQLExecutionInputCustomizer>
            ): RawGraphQLRequest.Builder {
                if (this.executionInputCustomizers.isEmpty()) {
                    this.executionInputCustomizers =
                        executionInputCustomizers.toPersistentList().builder()
                } else {
                    this.executionInputCustomizers.addAll(executionInputCustomizers)
                }
                return this
            }

            override fun build(): RawGraphQLRequest {
                val validatedRequestId: UUID =
                    if (requestId == UNSET_REQUEST_ID) {
                        headers.id!!
                    } else {
                        requestId
                    }
                val validatedExecutionId: ExecutionId =
                    if (executionId == UNSET_EXECUTION_ID) {
                        ExecutionId.from(validatedRequestId.toString())
                    } else {
                        executionId
                    }
                return when {
                    rawGraphQLQueryText == UNSET_RAW_GRAPHQL_QUERY_TEXT &&
                        expectedOutputFieldNames.isEmpty() -> {
                        throw ServiceError.invalidRequestErrorBuilder()
                            .message(
                                "either raw_graphql_query_text or expected_output_field_names must be provided"
                            )
                            .build()
                    }
                    else -> {
                        DefaultRawGraphQLRequest(
                            requestId = validatedRequestId,
                            executionId = validatedExecutionId,
                            uri = uri,
                            headers = headers,
                            principalPublisher = principalPublisher,
                            rawGraphQLQueryText = rawGraphQLQueryText,
                            operationName = operationName,
                            variables = variables.toPersistentMap(),
                            locale = locale,
                            expectedOutputFieldNames = expectedOutputFieldNames.toPersistentList(),
                            executionInputCustomizers = executionInputCustomizers.toPersistentList()
                        )
                    }
                }
            }
        }
    }

    override fun builder(): RawGraphQLRequest.Builder {
        return DefaultRawGraphQLRequestBuilder()
    }
}
