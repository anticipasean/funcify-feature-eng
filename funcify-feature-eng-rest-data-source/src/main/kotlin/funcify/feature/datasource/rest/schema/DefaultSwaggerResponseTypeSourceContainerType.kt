package funcify.feature.datasource.rest.schema

import arrow.core.Option
import funcify.feature.naming.ConventionalName
import funcify.feature.schema.dataelementsource.DataElementSource
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
internal data class DefaultSwaggerResponseTypeSourceContainerType(
    override val dataSourceLookupKey: DataElementSource.Key<RestApiSourceIndex>,
    override val sourcePath: SchematicPath,
    override val name: ConventionalName,
    override val responseJsonSchema: Option<Schema<*>>,
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
