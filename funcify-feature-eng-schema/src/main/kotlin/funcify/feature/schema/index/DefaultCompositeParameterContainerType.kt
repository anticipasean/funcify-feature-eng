package funcify.feature.schema.index

import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.ParameterContainerType
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

/**
 *
 * @author smccarron
 * @created 2022-06-18
 */
internal data class DefaultCompositeParameterContainerType(
    override val conventionalName: ConventionalName,
    private val parameterContainerTypeByDataSource:
        PersistentMap<DataSource.Key<*>, ParameterContainerType> =
        persistentMapOf()
) : CompositeParameterContainerType {

    override fun getParameterContainerTypeByDataSource():
        ImmutableMap<DataSource.Key<*>, ParameterContainerType> {
        return parameterContainerTypeByDataSource
    }
}
