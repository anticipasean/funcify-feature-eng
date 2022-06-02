package funcify.feature.schema.index

import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.SourceAttribute
import funcify.feature.tools.extensions.PersistentMapExtensions.streamEntries
import kotlinx.collections.immutable.ImmutableMap

/**
 *
 * @author smccarron
 * @created 1/30/22
 */
interface CompositeAttribute : CompositeIndex {

    override fun canBeSourcedFrom(sourceType: DataSourceType): Boolean =
        getSourceAttributeByDataSource().streamEntries().anyMatch { e ->
            e.key.sourceType == sourceType
        }

    fun getSourceAttributeByDataSource(): ImmutableMap<DataSource.Key<*>, SourceAttribute>
}
