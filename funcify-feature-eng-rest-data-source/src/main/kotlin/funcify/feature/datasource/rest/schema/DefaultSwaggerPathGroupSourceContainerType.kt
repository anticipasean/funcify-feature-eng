package funcify.feature.datasource.rest.schema

import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import io.swagger.v3.oas.models.PathItem
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

/**
 *
 * @author smccarron
 * @created 2022-07-10
 */
internal data class DefaultSwaggerPathGroupSourceContainerType(
    override val dataSourceLookupKey: DataSource.Key<RestApiSourceIndex>,
    override val sourcePath: SchematicPath,
    override val name: ConventionalName,
    override val pathItemsBySchematicPath: PersistentMap<SchematicPath, Pair<String, PathItem>>,
    override val sourceAttributes: PersistentSet<SwaggerSourceAttribute> = persistentSetOf()
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
