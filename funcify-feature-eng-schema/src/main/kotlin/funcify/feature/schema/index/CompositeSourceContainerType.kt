package funcify.feature.schema.index

import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.SourceAttribute
import funcify.feature.schema.datasource.SourceContainerType
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.tools.extensions.PersistentMapExtensions.streamEntries
import kotlinx.collections.immutable.ImmutableMap

/**
 *
 * @author smccarron
 * @created 1/30/22
 */
interface CompositeSourceContainerType : CompositeSourceIndex {

    override fun canBeSourcedFrom(sourceType: DataSourceType): Boolean {
        return getSourceContainerTypeByDataSource().streamEntries().anyMatch { e ->
            e.key.dataSourceType == sourceType
        }
    }

    override fun canBeSourcedFrom(sourceName: String): Boolean {
        return getSourceContainerTypeByDataSource().streamEntries().anyMatch { e ->
            e.key.name == sourceName
        }
    }

    fun getSourceContainerTypeByDataSource():
        ImmutableMap<DataSource.Key<*>, SourceContainerType<*, *>>

    fun <SI : SourceIndex<SI>, SA : SourceAttribute<SI>> getSourceContainerTypeForDataSourceKey(
        key: DataSource.Key<SI>
    ): SourceContainerType<SI, SA>? {
        @Suppress("UNCHECKED_CAST") //
        return getSourceContainerTypeByDataSource()[key] as? SourceContainerType<SI, SA>
    }
}
