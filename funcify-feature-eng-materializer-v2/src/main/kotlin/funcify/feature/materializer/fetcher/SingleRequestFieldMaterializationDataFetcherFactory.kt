package funcify.feature.materializer.fetcher

import graphql.execution.DataFetcherResult
import graphql.schema.DataFetcher
import graphql.schema.DataFetcherFactory
import graphql.schema.DataFetcherFactoryEnvironment
import java.util.concurrent.CompletionStage

/**
 * @author smccarron
 * @created 2022-08-03
 */
interface SingleRequestFieldMaterializationDataFetcherFactory :
    DataFetcherFactory<CompletionStage<out DataFetcherResult<Any?>>> {

    override fun get(
        environment: DataFetcherFactoryEnvironment?
    ): DataFetcher<CompletionStage<out DataFetcherResult<Any?>>>
}
