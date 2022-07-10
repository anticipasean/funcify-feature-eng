package funcify.feature.datasource.rest.schema

import funcify.feature.datasource.rest.schema.RestApiSourceIndex
import funcify.feature.datasource.rest.schema.SwaggerRestApiSourceIndex
import funcify.feature.schema.datasource.SourceAttribute

/**
 *
 * @author smccarron
 * @created 2022-07-10
 */
interface SwaggerSourceAttribute : SwaggerRestApiSourceIndex, SourceAttribute<RestApiSourceIndex> {}
