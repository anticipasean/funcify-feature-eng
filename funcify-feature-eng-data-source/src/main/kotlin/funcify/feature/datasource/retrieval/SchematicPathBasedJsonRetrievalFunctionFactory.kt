package funcify.feature.datasource.retrieval

import kotlinx.collections.immutable.ImmutableSet

/**
 *
 * @author smccarron
 * @created 2022-08-11
 */
interface SchematicPathBasedJsonRetrievalFunctionFactory {

    val dataSourceSpecificJsonRetrievalStrategies:
        ImmutableSet<DataSourceSpecificJsonRetrievalStrategy<*>>

    fun builder(): SchematicPathBasedJsonRetrievalFunction.Builder
}
