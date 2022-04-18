package funcify.feature.materializer.service

import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.response.SerializedGraphQLResponse
import funcify.feature.tools.container.async.Async

/**
 *
 * @author smccarron
 * @created 2/13/22
 */
interface GraphQLRequestExecutor {

    fun executeSingleRequest(rawGraphQLRequest: RawGraphQLRequest): Async<SerializedGraphQLResponse>
}
