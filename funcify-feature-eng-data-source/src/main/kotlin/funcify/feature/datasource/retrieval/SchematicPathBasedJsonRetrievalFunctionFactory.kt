package funcify.feature.datasource.retrieval

import funcify.feature.schema.datasource.DataSource

/**
 *
 * @author smccarron
 * @created 2022-08-11
 */
interface SchematicPathBasedJsonRetrievalFunctionFactory {

    fun canBuildExternalDataSourceJsonValuesRetrieverForDataSource(
        dataSourceKey: DataSource.Key<*>
    ): Boolean

    fun canBuildTrackableJsonValueRetrieverOnBehalfOfDataSource(
        dataSourceKey: DataSource.Key<*>
    ): Boolean

    fun externalDataSourceJsonValuesRetrieverBuilder():
        ExternalDataSourceJsonValuesRetriever.Builder

    fun trackableValueJsonRetrievalFunctionBuilder(): TrackableJsonValueRetriever.Builder
}
