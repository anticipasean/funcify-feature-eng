package funcify.feature.schema.dataelementsource.retrieval

import funcify.feature.schema.dataelementsource.DataElementSource

/**
 *
 * @author smccarron
 * @created 2022-08-11
 */
interface SchematicPathBasedJsonRetrievalFunctionFactory {

    fun canBuildExternalDataSourceJsonValuesRetrieverForDataSource(
        dataSourceKey: DataElementSource.Key<*>
    ): Boolean

    fun canBuildTrackableJsonValueRetrieverOnBehalfOfDataSource(
        dataSourceKey: DataElementSource.Key<*>
    ): Boolean

    fun dataElementJsonValueStoreBuilder(): DataElementJsonValueSource.Builder

    fun featureJsonValueStoreBuilder(): FeatureJsonValueStore.Builder
}
