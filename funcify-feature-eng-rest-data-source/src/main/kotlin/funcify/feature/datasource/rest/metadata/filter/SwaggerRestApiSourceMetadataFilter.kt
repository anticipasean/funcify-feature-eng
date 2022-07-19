package funcify.feature.datasource.rest.metadata.filter

import funcify.feature.schema.path.SchematicPath
import io.swagger.v3.oas.models.PathItem

/**
 *
 * @author smccarron
 * @created 2022-07-19
 */
fun interface SwaggerRestApiSourceMetadataFilter {

    companion object {

        val INCLUDE_ALL_FILTER: SwaggerRestApiSourceMetadataFilter =
            SwaggerRestApiSourceMetadataFilter { sp: SchematicPath, pi: PathItem ->
                true
            }
    }

    fun includeServicePath(sourcePath: SchematicPath, pathItem: PathItem): Boolean
}
