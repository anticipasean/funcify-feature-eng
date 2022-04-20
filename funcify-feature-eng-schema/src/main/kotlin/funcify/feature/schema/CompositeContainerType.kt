package funcify.feature.schema

import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.SourceContainerType
import funcify.feature.tools.extensions.PersistentMapExtensions.streamEntries
import kotlinx.collections.immutable.ImmutableMap

/**
 *
 * @author smccarron
 * @created 1/30/22
 */
interface CompositeContainerType : CompositeIndex {

    override fun canBeSourcedFrom(sourceType: DataSourceType): Boolean =
        getSourceContainerTypeByDataSource().streamEntries().anyMatch { e ->
            e.key.sourceType == sourceType
        }

    fun getSourceContainerTypeByDataSource():
        ImmutableMap<DataSource<*>, SourceContainerType<*>>
}
