package funcify.feature.datasource.rest.schema

import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.DataElementSource
import funcify.feature.schema.path.SchematicPath

/**
 *
 * @author smccarron
 * @created 2022-07-20
 */
internal data class DefaultSwaggerSourcePathItemGroupAttribute(
    override val sourcePath: SchematicPath,
    override val dataSourceLookupKey: DataElementSource.Key<RestApiSourceIndex>,
    override val name: ConventionalName
) : SwaggerSourceAttribute {}
