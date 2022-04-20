package funcify.feature.schema.index

import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceContainerType
import funcify.feature.schema.datasource.SourceIndex
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

internal data class DefaultCompositeContainerType(
    override val conventionalName: ConventionalName,
    private val sourceContainerTypesByDataSource:
        PersistentMap<DataSource<*>, SourceContainerType<*>> =
        persistentMapOf()
) : CompositeContainerType {

    fun <SI : SourceIndex, SCT : SourceContainerType<SA>, SA> put(
        dataSource: DataSource<SI>,
        sourceContainerType: SCT
    ): DefaultCompositeContainerType where SA : SI {
        return DefaultCompositeContainerType(
            conventionalName = conventionalName,
            sourceContainerTypesByDataSource =
                sourceContainerTypesByDataSource.put(dataSource, sourceContainerType)
        )
    }

    override fun getSourceContainerTypeByDataSource():
        ImmutableMap<DataSource<*>, SourceContainerType<*>> {
        return sourceContainerTypesByDataSource
    }
}
