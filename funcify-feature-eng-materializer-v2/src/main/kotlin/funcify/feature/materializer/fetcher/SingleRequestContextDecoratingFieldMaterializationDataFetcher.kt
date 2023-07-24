package funcify.feature.materializer.fetcher

import graphql.execution.DataFetcherResult
import graphql.schema.DataFetcher
import java.util.concurrent.CompletionStage

/**
 *
 * @author smccarron
 * @created 2022-07-13
 */
interface SingleRequestContextDecoratingFieldMaterializationDataFetcher<R> :
    DataFetcher<CompletionStage<out DataFetcherResult<R>>> {}
