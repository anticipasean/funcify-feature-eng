package funcify.feature.schema.index

import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceAttribute
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

internal data class DefaultCompositeAttribute(
    override val conventionalName: ConventionalName,
    private val sourceAttributesByDataSource: PersistentMap<DataSource<*>, SourceAttribute> =
        persistentMapOf()
) : CompositeAttribute {

    override fun getSourceAttributeByDataSource(): ImmutableMap<DataSource<*>, SourceAttribute> {
        return sourceAttributesByDataSource
    }
}
