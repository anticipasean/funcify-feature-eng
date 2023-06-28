package funcify.feature.schema.index

import funcify.feature.schema.datasource.DataElementSource
import funcify.feature.schema.datasource.SourceType
import funcify.feature.schema.datasource.ParameterAttribute
import funcify.feature.schema.datasource.ParameterContainerType
import funcify.feature.schema.datasource.SourceIndex
import funcify.feature.tools.extensions.PersistentMapExtensions.streamEntries
import kotlinx.collections.immutable.ImmutableMap

/**
 *
 * @author smccarron
 * @created 2022-06-18
 */
interface CompositeParameterContainerType : CompositeParameterIndex {

    override fun canBeProvidedTo(sourceType: SourceType): Boolean {
        return getParameterContainerTypeByDataSource().streamEntries().anyMatch { entry ->
            entry.key.sourceType == sourceType
        }
    }

    override fun canBeProvidedTo(sourceName: String): Boolean {
        return getParameterContainerTypeByDataSource().streamEntries().anyMatch { entry ->
            entry.key.name == sourceName
        }
    }

    fun getParameterContainerTypeByDataSource():
        ImmutableMap<DataElementSource.Key<*>, ParameterContainerType<*, *>>

    fun <
        SI : SourceIndex<SI>,
        PA : ParameterAttribute<SI>> getParameterContainerTypeForDataSourceKey(
        key: DataElementSource.Key<SI>
    ): ParameterContainerType<SI, PA>? {
        @Suppress("UNCHECKED_CAST") //
        return getParameterContainerTypeByDataSource()[key] as? ParameterContainerType<SI, PA>
    }
}
