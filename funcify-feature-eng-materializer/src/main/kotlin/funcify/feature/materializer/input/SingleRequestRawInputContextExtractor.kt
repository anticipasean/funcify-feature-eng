package funcify.feature.materializer.input

import funcify.feature.materializer.session.request.GraphQLSingleRequestSession
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-07-31
 */
interface SingleRequestRawInputContextExtractor :
    MaterializationRawInputContextExtractor<GraphQLSingleRequestSession> {

    override fun extractRawInputContextIfProvided(
        session: GraphQLSingleRequestSession
    ): Mono<out GraphQLSingleRequestSession>
}
