package funcify.feature.datasource.rest.schema

import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

/**
 *
 * @author smccarron
 * @created 2022-07-10
 */
internal data class DefaultSwaggerParameterContainerType(
    override val dataSourceLookupKey: DataSource.Key<RestApiSourceIndex>,
    override val sourcePath: SchematicPath,
    override val name: ConventionalName,
    override val parameterAttributes: PersistentSet<SwaggerParameterAttribute> = persistentSetOf()
) : SwaggerParameterContainerType {

    private val parameterAttributesByName:
        PersistentMap<String, SwaggerParameterAttribute> by lazy {
        parameterAttributes.asSequence().fold(persistentMapOf()) { pm, paramAttr ->
            pm.put(paramAttr.name.toString(), paramAttr)
        }
    }

    override fun getParameterAttributeWithName(name: String): SwaggerParameterAttribute? {
        return parameterAttributesByName[name]
    }
}
