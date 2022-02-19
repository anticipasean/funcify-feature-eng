package funcify.feature.graphql.response

import graphql.ExecutionResult


/**
 *
 * @author smccarron
 * @created 2/13/22
 */
interface SerializedGraphQLResponse {

    val executionResult: ExecutionResult

}