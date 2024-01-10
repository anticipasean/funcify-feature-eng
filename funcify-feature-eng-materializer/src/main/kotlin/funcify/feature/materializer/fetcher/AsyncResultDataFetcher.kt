package funcify.feature.materializer.fetcher

import graphql.execution.DataFetcherResult
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import java.util.concurrent.CompletionStage

/**
 * @author smccarron
 * @created 2022-07-13
 */
interface AsyncResultDataFetcher<R> : DataFetcher<CompletionStage<out DataFetcherResult<R>>> {

    override fun get(
        environment: DataFetchingEnvironment?
    ): CompletionStage<out DataFetcherResult<R>>
}
