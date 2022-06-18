package funcify.feature.schema.index

import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.ParameterAttribute
import funcify.feature.tools.extensions.PersistentMapExtensions.streamEntries
import kotlinx.collections.immutable.ImmutableMap

/**
 *
 * @author smccarron
 * @created 2022-06-18
 */
interface CompositeParameterAttribute : CompositeParameterIndex {

    override fun canBeProvidedTo(sourceType: DataSourceType): Boolean {
        return getParameterAttributesByDataSource().streamEntries().anyMatch { entry ->
            entry.key.sourceType == sourceType
        }
    }

    fun getParameterAttributesByDataSource(): ImmutableMap<DataSource.Key<*>, ParameterAttribute>
}
