package funcify.feature.materializer.executor

import graphql.execution.AsyncExecutionStrategy
import graphql.execution.DataFetcherExceptionHandler

/**
 * @author smccarron
 * @created 2022-09-03
 */
abstract class GraphQLSingleRequestMaterializationQueryExecutionStrategy(
    private val dataFetcherExceptionHandler: DataFetcherExceptionHandler
) : AsyncExecutionStrategy(dataFetcherExceptionHandler)
