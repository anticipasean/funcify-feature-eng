package funcify.feature.schema

import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.SourceContainerType
import kotlinx.collections.immutable.ImmutableMap


/**
 *
 * @author smccarron
 * @created 1/30/22
 */
interface CompositeContainerType : CompositeIndex {

    override fun canBeSourcedFrom(sourceType: DataSourceType): Boolean = getSourceContainerTypeByDataSourceType().containsKey(sourceType);

    fun getSourceContainerTypeByDataSourceType(): ImmutableMap<DataSourceType, SourceContainerType<*>>

}