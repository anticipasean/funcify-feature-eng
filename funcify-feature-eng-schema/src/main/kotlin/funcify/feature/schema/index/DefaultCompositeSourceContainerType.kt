package funcify.feature.schema.index

import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceContainerType
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

internal data class DefaultCompositeSourceContainerType(
    override val conventionalName: ConventionalName,
    private val sourceContainerTypesByDataSource:
        PersistentMap<DataSource.Key<*>, SourceContainerType<*, *>> =
        persistentMapOf()
) : CompositeSourceContainerType {

    override fun getSourceContainerTypeByDataSource():
        ImmutableMap<DataSource.Key<*>, SourceContainerType<*, *>> {
        return sourceContainerTypesByDataSource
    }
}
