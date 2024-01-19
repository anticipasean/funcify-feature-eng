package funcify.feature.materializer.session.factory

import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.session.request.GraphQLSingleRequestSession
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2/20/22
 */
interface GraphQLSingleRequestSessionFactory {

    fun createSessionForSingleRequest(
        rawGraphQLRequest: RawGraphQLRequest
    ): Mono<out GraphQLSingleRequestSession>
}
