package funcify.feature.datasource.rest.schema

import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath

/**
 *
 * @author smccarron
 * @created 2022-07-20
 */
internal data class DefaultSwaggerSourcePathItemGroupAttribute(
    override val sourcePath: SchematicPath,
    override val dataSourceLookupKey: DataSource.Key<RestApiSourceIndex>,
    override val name: ConventionalName
) : SwaggerSourceAttribute {}
