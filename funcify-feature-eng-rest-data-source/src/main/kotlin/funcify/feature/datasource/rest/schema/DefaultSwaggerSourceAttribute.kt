package funcify.feature.datasource.rest.schema

import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath

/**
 *
 * @author smccarron
 * @created 2022-07-10
 */
internal data class DefaultSwaggerSourceAttribute(
    override val dataSourceLookupKey: DataSource.Key<RestApiSourceIndex>,
    override val name: ConventionalName,
    override val sourcePath: SchematicPath
) : SwaggerSourceAttribute {}
