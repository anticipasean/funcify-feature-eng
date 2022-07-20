package funcify.feature.datasource.rest.schema

import arrow.core.Option
import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import io.swagger.v3.oas.models.PathItem

/**
 *
 * @author smccarron
 * @created 2022-07-20
 */
internal data class DefaultSwaggerSourcePathItemAttribute(
    override val sourcePath: SchematicPath,
    override val dataSourceLookupKey: DataSource.Key<RestApiSourceIndex>,
    override val name: ConventionalName,
    override val pathItem: Option<PathItem>
) : SwaggerSourceAttribute {

}
