package funcify.feature.schema.index

import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.SourceAttribute
import funcify.feature.schema.datasource.SourceContainerType
import funcify.feature.schema.datasource.SourceIndex
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

internal data class DefaultCompositeAttribute(
    override val conventionalName: ConventionalName,
    private val sourceAttributesByDataSource: PersistentMap<DataSource<*>, SourceAttribute> =
        persistentMapOf()
) : CompositeAttribute {

    fun <SI : SourceIndex, SCT : SourceContainerType<SA>, SA> put(
        dataSource: DataSource<SI>,
        sourceAttribute: SA
    ): DefaultCompositeAttribute where SA : SI {
        return DefaultCompositeAttribute(
            conventionalName = conventionalName,
            sourceAttributesByDataSource =
                sourceAttributesByDataSource.put(
                    dataSource, /* cast is sound since SA must
                                 * be a source attribute per source
                                 * container type's bounds for SA
                                 */
                    sourceAttribute as SourceAttribute
                )
        )
    }
    override fun getSourceAttributeByDataSource(): ImmutableMap<DataSource<*>, SourceAttribute> {
        return sourceAttributesByDataSource
    }
}
