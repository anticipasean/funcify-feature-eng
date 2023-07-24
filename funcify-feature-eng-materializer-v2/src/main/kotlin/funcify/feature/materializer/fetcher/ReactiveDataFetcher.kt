package funcify.feature.materializer.fetcher

import graphql.schema.DataFetchingEnvironment
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-07-22
 */
interface ReactiveDataFetcher<T : Any> : (DataFetchingEnvironment) -> Mono<T> {

    override fun invoke(environment: DataFetchingEnvironment): Mono<T>
}
