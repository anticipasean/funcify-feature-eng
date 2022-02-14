package funcify.graphql.service

import funcify.graphql.request.RawGraphQLRequest
import funcify.graphql.response.SerializedGraphQLResponse
import reactor.core.publisher.Mono


/**
 *
 * @author smccarron
 * @created 2/13/22
 */
interface GraphQLRequestExecutor {

    fun execute(rawGraphQLRequest: RawGraphQLRequest): Mono<SerializedGraphQLResponse>

}