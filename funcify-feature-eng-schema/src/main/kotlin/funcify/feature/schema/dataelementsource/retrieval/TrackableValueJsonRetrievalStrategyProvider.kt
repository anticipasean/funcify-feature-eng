package funcify.feature.schema.dataelementsource.retrieval

import funcify.feature.schema.dataelementsource.DataElementSource
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
    ): Try<FeatureJsonValueStore>
}
