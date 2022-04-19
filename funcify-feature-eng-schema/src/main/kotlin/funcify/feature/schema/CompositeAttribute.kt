package funcify.feature.schema

import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.SourceAttribute
import kotlinx.collections.immutable.ImmutableMap

/**
 *
 * @author smccarron
 * @created 1/30/22
 */
interface CompositeAttribute : CompositeIndex {

    override fun canBeSourcedFrom(sourceType: DataSourceType): Boolean =
        getSourceAttributeByDataSourceType().containsKey(sourceType)

    fun getSourceAttributeByDataSourceType(): ImmutableMap<DataSourceType, SourceAttribute>
}
