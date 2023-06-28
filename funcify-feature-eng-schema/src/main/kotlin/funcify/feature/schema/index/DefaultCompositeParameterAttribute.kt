package funcify.feature.schema.index

import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.DataElementSource
import funcify.feature.schema.datasource.ParameterAttribute
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

/**
 *
 * @author smccarron
 * @created 2022-06-18
 */
internal data class DefaultCompositeParameterAttribute(
    override val conventionalName: ConventionalName,
    private val parameterAttributeByDataSource:
        PersistentMap<DataElementSource.Key<*>, ParameterAttribute<*>> =
        persistentMapOf()
) : CompositeParameterAttribute {

    override fun getParameterAttributesByDataSource():
        ImmutableMap<DataElementSource.Key<*>, ParameterAttribute<*>> {
        return parameterAttributeByDataSource
    }
}
