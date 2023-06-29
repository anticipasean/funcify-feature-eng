package funcify.feature.datasource.rest.schema

import arrow.core.Option
import arrow.core.none
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.media.Schema

/**
 *
 * @author smccarron
 * @created 2022-07-10
 */
interface SwaggerSourceAttribute : SwaggerRestApiSourceIndex, SourceAttribute<RestApiSourceIndex> {

    val servicePathItemName: Option<String>
        get() = none()

    val pathItem: Option<PathItem>
        get() = none()

    val responseBodyJsonPropertyName: Option<String>
        get() = none()
    // TODO: Consider whether this swagger model json schema type or the one from the
    //  jackson framework should be used
    val responseBodyPropertyJsonSchema: Option<Schema<*>>
        get() = none()

    fun representsPathItem(): Boolean {
        return servicePathItemName.isDefined() && pathItem.isDefined()
    }

    fun representsResponseBodyProperty(): Boolean {
        return responseBodyJsonPropertyName.isDefined() &&
            responseBodyPropertyJsonSchema.isDefined()
    }

    fun representsPathItemGroup(): Boolean {
        return !pathItem.isDefined() &&
            !(responseBodyJsonPropertyName.isDefined() &&
                responseBodyPropertyJsonSchema.isDefined())
    }
}
