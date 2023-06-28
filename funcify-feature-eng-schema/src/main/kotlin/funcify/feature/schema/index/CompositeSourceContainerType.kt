package funcify.feature.schema.index

import funcify.feature.schema.datasource.DataElementSource
import funcify.feature.schema.datasource.SourceType
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

    override fun canBeSourcedFrom(sourceType: SourceType): Boolean {
        return getSourceContainerTypeByDataSource().streamEntries().anyMatch { e ->
            e.key.sourceType == sourceType
        }
    }

    override fun canBeSourcedFrom(sourceName: String): Boolean {
        return getSourceContainerTypeByDataSource().streamEntries().anyMatch { e ->
            e.key.name == sourceName
        }
    }

    fun getSourceContainerTypeByDataSource():
        ImmutableMap<DataElementSource.Key<*>, SourceContainerType<*, *>>

    fun <SI : SourceIndex<SI>, SA : SourceAttribute<SI>> getSourceContainerTypeForDataSourceKey(
        key: DataElementSource.Key<SI>
    ): SourceContainerType<SI, SA>? {
        @Suppress("UNCHECKED_CAST") //
        return getSourceContainerTypeByDataSource()[key] as? SourceContainerType<SI, SA>
    }
}
