package funcify.feature.datasource.rest.schema

import arrow.core.Option
import arrow.core.none
import funcify.feature.schema.datasource.SourceAttribute
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.media.Schema

/**
 *
 * @author smccarron
 * @created 2022-07-10
 */
interface SwaggerSourceAttribute : SwaggerRestApiSourceIndex, SourceAttribute<RestApiSourceIndex> {

    val pathItem: Option<PathItem>
        get() = none()

    val responseBodyJsonPropertyName: Option<String>
        get() = none()
    // TODO: Consider whether this swagger model json schema type or the one from the
    //  jackson framework should be used
    val responseBodyPropertyJsonSchema: Option<Schema<*>>
        get() = none()

    fun representsPathItem(): Boolean {
        return pathItem.isDefined()
    }

    fun representsResponseBodyProperty(): Boolean {
        return responseBodyJsonPropertyName.isDefined() &&
            responseBodyPropertyJsonSchema.isDefined()
    }
}
