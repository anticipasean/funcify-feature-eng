package funcify.feature.datasource.rest.schema

import arrow.core.Option
import arrow.core.none
import funcify.feature.schema.datasource.SourceContainerType
import funcify.feature.schema.path.SchematicPath
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.media.Schema
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf

/**
 *
 * @author smccarron
 * @created 2022-07-10
 */
interface SwaggerSourceContainerType :
    SwaggerRestApiSourceIndex, SourceContainerType<RestApiSourceIndex, SwaggerSourceAttribute> {

    val pathItemsBySchematicPath: ImmutableMap<SchematicPath, PathItem>
        get() = persistentMapOf()

    // TODO: Investigate whether this schema type should be the one used in the swagger model or
    //   a "parsed" form in the jackson framework:
    //   [com.fasterxml.jackson.module.jsonSchema.JsonSchema]
    val responseJsonSchema: Option<Schema<*>>
        get() = none()

    fun isResponseType(): Boolean {
        return responseJsonSchema.isDefined()
    }

    fun isPathGroup(): Boolean {
        return pathItemsBySchematicPath.isNotEmpty()
    }
}
