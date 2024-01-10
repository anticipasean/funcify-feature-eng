package funcify.feature.materializer.response

import arrow.core.Option
import com.fasterxml.jackson.databind.JsonNode
import graphql.ExecutionResult

/**
 * @author smccarron
 * @created 2/13/22
 */
interface SerializedGraphQLResponse {

    val executionResult: ExecutionResult

    val resultAsColumnarJsonObject: Option<JsonNode>

    interface Builder {

        fun executionResult(executionResult: ExecutionResult): Builder

        fun resultAsColumnarJsonObject(columnarJsonObject: JsonNode): Builder

        fun build(): SerializedGraphQLResponse
    }
}
