package funcify.feature.schema.index

import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.DataElementSource
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
        PersistentMap<DataElementSource.Key<*>, ParameterContainerType<*, *>> =
        persistentMapOf()
) : CompositeParameterContainerType {

    override fun getParameterContainerTypeByDataSource():
        ImmutableMap<DataElementSource.Key<*>, ParameterContainerType<*, *>> {
        return parameterContainerTypeByDataSource
    }
}
