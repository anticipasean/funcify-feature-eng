package funcify.feature.graphql.service

import funcify.feature.graphql.request.RawGraphQLRequest
import funcify.feature.graphql.response.SerializedGraphQLResponse
import funcify.feature.tools.container.async.Async


/**
 *
 * @author smccarron
 * @created 2/13/22
 */
interface GraphQLRequestExecutor {

    fun executeRequest(rawGraphQLRequest: RawGraphQLRequest): Async<SerializedGraphQLResponse>

}