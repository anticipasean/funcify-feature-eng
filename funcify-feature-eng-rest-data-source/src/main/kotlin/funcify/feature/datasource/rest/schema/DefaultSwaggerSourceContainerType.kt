package funcify.feature.datasource.rest.schema

import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf

/**
 *
 * @author smccarron
 * @created 2022-07-10
 */
internal data class DefaultSwaggerSourceContainerType(
    override val sourceAttributes: PersistentSet<SwaggerSourceAttribute>,
    override val dataSourceLookupKey: DataSource.Key<RestApiSourceIndex>,
    override val name: ConventionalName,
    override val sourcePath: SchematicPath
) : SwaggerSourceContainerType {

    private val sourceAttributesByName: PersistentMap<String, SwaggerSourceAttribute> by lazy {
        sourceAttributes.asSequence().fold(persistentMapOf()) { pm, sourceAttr ->
            pm.put(sourceAttr.name.toString(), sourceAttr)
        }
    }

    override fun getSourceAttributeWithName(name: String): SwaggerSourceAttribute? {
        return sourceAttributesByName[name]
    }
}
