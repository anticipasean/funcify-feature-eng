package funcify.feature.schema.index

import funcify.feature.schema.datasource.DataElementSource
import funcify.feature.schema.datasource.SourceType
import funcify.feature.schema.datasource.ParameterAttribute
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.tools.extensions.PersistentMapExtensions.streamEntries
import kotlinx.collections.immutable.ImmutableMap

/**
 *
 * @author smccarron
 * @created 2022-06-18
 */
interface CompositeParameterAttribute : CompositeParameterIndex {

    override fun canBeProvidedTo(sourceType: SourceType): Boolean {
        return getParameterAttributesByDataSource().streamEntries().anyMatch { entry ->
            entry.key.sourceType == sourceType
        }
    }

    override fun canBeProvidedTo(sourceName: String): Boolean {
        return getParameterAttributesByDataSource().streamEntries().anyMatch { entry ->
            entry.key.name == sourceName
        }
    }

    fun getParameterAttributesByDataSource(): ImmutableMap<DataElementSource.Key<*>, ParameterAttribute<*>>

    fun <SI : SourceIndex<SI>> getParameterAttributeForDataSourceKey(
        key: DataElementSource.Key<SI>
    ): ParameterAttribute<SI>? {
        @Suppress("UNCHECKED_CAST") //
        return getParameterAttributesByDataSource()[key] as? ParameterAttribute<SI>
    }
}
