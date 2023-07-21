package funcify.feature.materializer.request

import funcify.feature.error.ServiceError
import funcify.feature.schema.path.SchematicPath
import graphql.execution.ExecutionId
import java.net.URI
import java.security.Principal
import java.util.*
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import org.springframework.messaging.MessageHeaders
import reactor.core.publisher.Mono

internal class DefaultRawGraphQLRequestFactory : RawGraphQLRequestFactory {

    companion object {

        private val UNSET_REQUEST_ID: UUID = UUID(0, 0)
        private val UNSET_EXECUTION_ID: ExecutionId = ExecutionId.from(UNSET_REQUEST_ID.toString())
        private val UNSET_URI: URI = SchematicPath.getRootPath().toURI()
        private const val UNSET_RAW_GRAPHQL_QUERY_TEXT: String = ""
        private const val UNSET_OPERATION_NAME: String = "DEFAULT_OPERATION"

        internal class DefaultRawGraphQLRequestBuilder(
            private var requestId: UUID = UNSET_REQUEST_ID,
            private var executionId: ExecutionId = UNSET_EXECUTION_ID,
            private var uri: URI = UNSET_URI,
            private var headers: MessageHeaders = MessageHeaders(mapOf()),
            private var principalPublisher: Mono<out Principal> = Mono.empty(),
            private var rawGraphQLQueryText: String = UNSET_RAW_GRAPHQL_QUERY_TEXT,
            private var operationName: String = UNSET_OPERATION_NAME,
            private var variables: MutableMap<String, Any?> = mutableMapOf(),
            private var locale: Locale = Locale.getDefault(),
            private var expectedOutputFieldNames: MutableList<String> = mutableListOf(),
            private var executionInputCustomizers: MutableList<GraphQLExecutionInputCustomizer> =
                mutableListOf()
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
                this.variables = variables.toMutableMap()
                return this
            }

            override fun variable(key: String, value: Any?): RawGraphQLRequest.Builder {
                this.variables[key] = value
                return this
            }

            override fun locale(locale: Locale): RawGraphQLRequest.Builder {
                this.locale = locale
                return this
            }

            override fun expectedOutputFieldNames(
                expectedOutputFieldNames: List<String>
            ): RawGraphQLRequest.Builder {
                this.expectedOutputFieldNames = expectedOutputFieldNames.toMutableList()
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
                this.executionInputCustomizers = executionInputCustomizers.toMutableList()
                return this
            }

            override fun build(): RawGraphQLRequest {
                val validatedRequestId: UUID =
                    if (requestId == UNSET_REQUEST_ID) {
                        UUID.randomUUID()
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
                    uri == UNSET_URI -> {
                        throw ServiceError.invalidRequestErrorBuilder()
                            .message("uri is missing")
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

        internal data class DefaultRawGraphQLRequest(
            override val requestId: UUID,
            override val executionId: ExecutionId,
            override val uri: URI,
            override val headers: MessageHeaders,
            override val principalPublisher: Mono<out Principal> = Mono.empty(),
            override val rawGraphQLQueryText: String = "",
            override val operationName: String = "",
            override val variables: PersistentMap<String, Any?> = persistentMapOf(),
            override val locale: Locale = Locale.getDefault(),
            override val expectedOutputFieldNames: PersistentList<String> = persistentListOf(),
            override val executionInputCustomizers:
                PersistentList<GraphQLExecutionInputCustomizer> =
                persistentListOf(),
        ) : RawGraphQLRequest {}
    }

    override fun builder(): RawGraphQLRequest.Builder {
        return DefaultRawGraphQLRequestBuilder()
    }
}
