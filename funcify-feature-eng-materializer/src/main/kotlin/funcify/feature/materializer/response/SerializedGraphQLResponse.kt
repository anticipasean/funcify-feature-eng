package funcify.feature.materializer.response

import graphql.ExecutionResult


/**
 *
 * @author smccarron
 * @created 2/13/22
 */
interface SerializedGraphQLResponse {

    val executionResult: ExecutionResult

}