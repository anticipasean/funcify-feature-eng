package funcify.feature.spring.service

import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.response.SerializedGraphQLResponse
import funcify.feature.tools.container.deferred.Deferred

/**
 *
 * @author smccarron
 * @created 2/13/22
 */
interface GraphQLSingleRequestExecutor {

    fun executeSingleRequest(
        rawGraphQLRequest: RawGraphQLRequest
    ): Deferred<SerializedGraphQLResponse>
}
