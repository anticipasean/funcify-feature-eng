package funcify.feature.datasource.rest.schema

import funcify.feature.schema.datasource.SourceContainerType

/**
 *
 * @author smccarron
 * @created 2022-07-10
 */
interface SwaggerSourceContainerType :
    SwaggerRestApiSourceIndex, SourceContainerType<RestApiSourceIndex, SwaggerSourceAttribute> {}
