package funcify.feature.materializer.fetcher

import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import graphql.schema.DataFetchingEnvironment
import org.slf4j.Logger
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-07-23
 */
class DummyReactiveDataFetcher<T : Any> : ReactiveDataFetcher<T> {

    companion object {
        private val logger: Logger = loggerFor<DummyReactiveDataFetcher<*>>()
    }
    override fun invoke(environment: DataFetchingEnvironment): Mono<T> {
        logger.info("invoke: [ environment.field.name: {} ]", environment.field.name)
        return Mono.empty()
    }
}
