package funcify.feature.datasource.retrieval

import funcify.feature.schema.datasource.DataElementSource
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.tools.container.attempt.Try

/**
 *
 * @author smccarron
 * @created 2022-08-15
 */
interface TrackableValueJsonRetrievalStrategyProvider<SI : SourceIndex<SI>> {

    fun providesJsonRetrievalFunctionsForVerticesWithSourceIndicesIn(
        dataSourceKey: DataElementSource.Key<*>
    ): Boolean

    fun createTrackableValueJsonRetrievalFunctionOnBehalfOf(
        dataSource: DataElementSource<SI>
    ): Try<TrackableJsonValueRetriever>
}
