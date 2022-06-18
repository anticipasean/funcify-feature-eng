package funcify.feature.schema.index

import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.ParameterContainerType
import funcify.feature.tools.extensions.PersistentMapExtensions.streamEntries
import kotlinx.collections.immutable.ImmutableMap

/**
 *
 * @author smccarron
 * @created 2022-06-18
 */
interface CompositeParameterContainerType : CompositeParameterIndex {

    override fun canBeProvidedTo(sourceType: DataSourceType): Boolean {
        return getParameterContainerTypeByDataSource().streamEntries().anyMatch { entry ->
            entry.key.sourceType == sourceType
        }
    }

    fun getParameterContainerTypeByDataSource():
        ImmutableMap<DataSource.Key<*>, ParameterContainerType>
}
