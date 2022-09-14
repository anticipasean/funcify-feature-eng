package funcify.feature.datasource.retrieval

import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.tools.container.attempt.Try

/**
 *
 * @author smccarron
 * @created 2022-08-15
 */
interface TrackableValueJsonRetrievalStrategyProvider<SI : SourceIndex<SI>> {

    fun providesJsonRetrievalFunctionsForVerticesWithSourceIndicesIn(
        dataSourceKey: DataSource.Key<*>
    ): Boolean

    fun createTrackableValueJsonRetrievalFunctionOnBehalfOf(
        dataSource: DataSource<SI>
    ): Try<TrackableJsonValueRetriever>
}
