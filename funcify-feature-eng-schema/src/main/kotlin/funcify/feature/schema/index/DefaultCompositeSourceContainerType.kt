package funcify.feature.schema.index

import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.DataElementSource
import funcify.feature.schema.datasource.SourceContainerType
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

internal data class DefaultCompositeSourceContainerType(
    override val conventionalName: ConventionalName,
    private val sourceContainerTypesByDataSource:
        PersistentMap<DataElementSource.Key<*>, SourceContainerType<*, *>> =
        persistentMapOf()
) : CompositeSourceContainerType {

    override fun getSourceContainerTypeByDataSource():
        ImmutableMap<DataElementSource.Key<*>, SourceContainerType<*, *>> {
        return sourceContainerTypesByDataSource
    }
}
