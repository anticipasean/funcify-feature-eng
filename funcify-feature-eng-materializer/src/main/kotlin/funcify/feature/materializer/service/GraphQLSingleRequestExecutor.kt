package funcify.feature.materializer.service

import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.response.SerializedGraphQLResponse
import funcify.feature.tools.container.deferred.Deferred
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2/13/22
 */
interface GraphQLSingleRequestExecutor {

    fun executeSingleRequest(
        rawGraphQLRequest: RawGraphQLRequest
    ): Mono<out SerializedGraphQLResponse>
}
