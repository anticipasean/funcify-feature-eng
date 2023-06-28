package funcify.feature.schema.index

import funcify.feature.schema.datasource.DataElementSource
import funcify.feature.schema.datasource.SourceType
import funcify.feature.schema.datasource.SourceAttribute
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.tools.extensions.PersistentMapExtensions.streamEntries
import kotlinx.collections.immutable.ImmutableMap

/**
 *
 * @author smccarron
 * @created 1/30/22
 */
interface CompositeSourceAttribute : CompositeSourceIndex {

    override fun canBeSourcedFrom(sourceType: SourceType): Boolean {
        return getSourceAttributeByDataSource().streamEntries().anyMatch { e ->
            e.key.sourceType == sourceType
        }
    }

    override fun canBeSourcedFrom(sourceName: String): Boolean {
        return getSourceAttributeByDataSource().streamEntries().anyMatch { e ->
            e.key.name == sourceName
        }
    }

    fun getSourceAttributeByDataSource(): ImmutableMap<DataElementSource.Key<*>, SourceAttribute<*>>

    fun <SI : SourceIndex<SI>, SA : SourceAttribute<SI>> getSourceAttributeForDataSourceKey(
        key: DataElementSource.Key<SI>
    ): SourceAttribute<SI>? {
        @Suppress("UNCHECKED_CAST") //
        return getSourceAttributeByDataSource()[key] as? SourceAttribute<SI>
    }
}
