package funcify.feature.datasource.retrieval

import funcify.feature.schema.datasource.DataSource

/**
 *
 * @author smccarron
 * @created 2022-08-11
 */
interface SchematicPathBasedJsonRetrievalFunctionFactory {

    fun canBuildMultipleSourceIndicesJsonRetrievalFunctionForDataSource(
        dataSourceKey: DataSource.Key<*>
    ): Boolean

    fun canBuildTrackableValueJsonRetrievalFunctionOnBehalfOfDataSource(
        dataSourceKey: DataSource.Key<*>
    ): Boolean

    fun multipleSourceIndicesJsonRetrievalFunctionBuilder():
        MultipleSourceIndicesJsonRetrievalFunction.Builder

    fun trackableValueJsonRetrievalFunctionBuilder(): TrackableValueJsonRetrievalFunction.Builder
}
