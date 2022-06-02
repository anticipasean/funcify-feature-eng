package funcify.feature.materializer.request

import arrow.core.getOrElse
import arrow.core.toOption
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import graphql.execution.ExecutionId
import java.net.URI
import java.util.*
import org.springframework.http.HttpHeaders

internal class DefaultRawGraphQLRequestFactory : RawGraphQLRequestFactory {

    companion object {

        private const val UNSET_REQUEST_ID: String = ""
        private val UNSET_EXECUTION_ID: ExecutionId = ExecutionId.from(UUID(0, 0).toString())
        private val UNSET_URI: URI = URI.create("mlfs:/")
        private const val UNSET_RAW_GRAPHQL_QUERY_TEXT: String = ""
        private const val UNSET_OPERATION_NAME: String = ""

        internal class DefaultRawGraphQLRequestBuilder(
            var requestId: String = UNSET_REQUEST_ID,
            var executionId: ExecutionId = UNSET_EXECUTION_ID,
            var uri: URI = UNSET_URI,
            var headers: HttpHeaders = HttpHeaders(),
            var rawGraphQLQueryText: String = UNSET_RAW_GRAPHQL_QUERY_TEXT,
            var operationName: String = UNSET_OPERATION_NAME,
            var variables: MutableMap<String, Any?> = mutableMapOf(),
            var locale: Locale = Locale.getDefault(),
            var executionInputCustomizers: MutableList<GraphQLExecutionInputCustomizer> =
                mutableListOf()
        ) : RawGraphQLRequest.Builder {

            override fun requestId(requestId: String): RawGraphQLRequest.Builder {
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

            override fun headers(headers: HttpHeaders): RawGraphQLRequest.Builder {
                this.headers = headers
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
                return when {
                    rawGraphQLQueryText == UNSET_RAW_GRAPHQL_QUERY_TEXT -> {
                        throw MaterializerException(
                            MaterializerErrorResponse.INVALID_GRAPHQL_REQUEST,
                            "raw_graphql_query_text is empty"
                        )
                    }
                    else -> {
                        DefaultRawGraphQLRequest(
                            requestId = requestId,
                            executionId = executionId,
                            uri = uri,
                            headers = headers,
                            rawGraphQLQueryText = rawGraphQLQueryText,
                            operationName = operationName,
                            variables = variables,
                            locale = locale,
                            executionInputCustomizers = executionInputCustomizers
                        )
                    }
                }
            }
        }

        internal data class DefaultRawGraphQLRequest(
            override val requestId: String = "",
            override val executionId: ExecutionId =
                requestId.toOption().filterNot(String::isEmpty).map(ExecutionId::from).getOrElse {
                    ExecutionId.generate()
                },
            override val uri: URI,
            override val headers: HttpHeaders,
            override val rawGraphQLQueryText: String = "",
            override val operationName: String = "",
            override val variables: Map<String, Any?> = mutableMapOf(),
            override val locale: Locale = Locale.getDefault(),
            override val executionInputCustomizers: List<GraphQLExecutionInputCustomizer> =
                mutableListOf()
        ) : RawGraphQLRequest {}
    }

    override fun builder(): RawGraphQLRequest.Builder {
        return DefaultRawGraphQLRequestBuilder()
    }
}
