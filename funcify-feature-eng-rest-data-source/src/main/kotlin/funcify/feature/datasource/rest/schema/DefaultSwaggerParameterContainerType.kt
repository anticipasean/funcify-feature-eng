package funcify.feature.datasource.rest.schema

import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.DataElementSource
import funcify.feature.schema.path.SchematicPath
import io.swagger.v3.oas.models.media.Schema
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
    override val dataSourceLookupKey: DataElementSource.Key<RestApiSourceIndex>,
    override val sourcePath: SchematicPath,
    override val name: ConventionalName,
    override val jsonSchema: Schema<*>,
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
