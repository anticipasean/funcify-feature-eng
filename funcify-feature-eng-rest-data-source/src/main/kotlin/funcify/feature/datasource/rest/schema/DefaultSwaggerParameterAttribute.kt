package funcify.feature.datasource.rest.schema

import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.DataElementSource
import funcify.feature.schema.path.SchematicPath
import io.swagger.v3.oas.models.media.Schema

/**
 *
 * @author smccarron
 * @created 2022-07-10
 */
internal data class DefaultSwaggerParameterAttribute(
    override val dataSourceLookupKey: DataElementSource.Key<RestApiSourceIndex>,
    override val sourcePath: SchematicPath,
    override val name: ConventionalName,
    override val jsonPropertyName: String,
    override val jsonSchema: Schema<*>
) : SwaggerParameterAttribute {}
