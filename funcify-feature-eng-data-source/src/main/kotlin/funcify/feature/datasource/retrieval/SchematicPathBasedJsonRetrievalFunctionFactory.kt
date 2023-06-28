package funcify.feature.datasource.retrieval

import funcify.feature.schema.datasource.DataElementSource

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

    fun externalDataSourceJsonValuesRetrieverBuilder():
        ExternalDataSourceJsonValuesRetriever.Builder

    fun trackableValueJsonRetrievalFunctionBuilder(): TrackableJsonValueRetriever.Builder
}
