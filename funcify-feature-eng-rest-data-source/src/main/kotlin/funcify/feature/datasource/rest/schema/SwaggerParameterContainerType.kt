package funcify.feature.datasource.rest.schema

import funcify.feature.schema.datasource.ParameterContainerType

/**
 *
 * @author smccarron
 * @created 2022-07-10
 */
interface SwaggerParameterContainerType :
    SwaggerRestApiSourceIndex,
    ParameterContainerType<RestApiSourceIndex, SwaggerParameterAttribute> {}
