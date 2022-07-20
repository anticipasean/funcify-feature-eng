package funcify.feature.datasource.rest.schema

import arrow.core.Option
import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import io.swagger.v3.oas.models.media.Schema

/**
 *
 * @author smccarron
 * @created 2022-07-10
 */
internal data class DefaultSwaggerSourceResponseBodyPropertyAttribute(
    override val dataSourceLookupKey: DataSource.Key<RestApiSourceIndex>,
    override val name: ConventionalName,
    override val sourcePath: SchematicPath,
    override val responseBodyJsonPropertyName: Option<String>,
    override val responseBodyPropertyJsonSchema: Option<Schema<*>>
) : SwaggerSourceAttribute {}
