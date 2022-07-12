package funcify.feature.datasource.rest.schema

import funcify.feature.schema.datasource.ParameterAttribute
import io.swagger.v3.oas.models.media.Schema

/**
 *
 * @author smccarron
 * @created 2022-07-10
 */
interface SwaggerParameterAttribute :
    SwaggerRestApiSourceIndex, ParameterAttribute<RestApiSourceIndex> {

    val jsonPropertyName: String
    // TODO: Consider whether this swagger model json schema type or the one from the
    //  jackson framework should be used
    val jsonSchema: Schema<*>
}
