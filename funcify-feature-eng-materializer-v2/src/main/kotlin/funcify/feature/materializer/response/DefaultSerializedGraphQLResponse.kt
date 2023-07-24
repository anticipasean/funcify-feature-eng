package funcify.feature.materializer.response

import arrow.core.Option
import arrow.core.none
import com.fasterxml.jackson.databind.JsonNode
import graphql.ExecutionResult

internal class DefaultSerializedGraphQLResponse(
    override val executionResult: ExecutionResult,
    override val resultAsColumnarJsonObject: Option<JsonNode> = none()
) : SerializedGraphQLResponse {}
