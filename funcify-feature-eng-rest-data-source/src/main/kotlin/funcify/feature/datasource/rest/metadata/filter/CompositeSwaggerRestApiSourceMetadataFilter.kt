package funcify.feature.datasource.rest.metadata.filter

import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import io.swagger.v3.oas.models.PathItem
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-07-19
 */
internal class CompositeSwaggerRestApiSourceMetadataFilter(
    val filters: List<SwaggerRestApiSourceMetadataFilter>
) : SwaggerRestApiSourceMetadataFilter {

    companion object {
        private val logger: Logger = loggerFor<CompositeSwaggerRestApiSourceMetadataFilter>()
    }

    override fun includeServicePath(
        sourcePath: SchematicPath,
        servicePathName: String,
        pathItem: PathItem
    ): Boolean {
        logger.debug(
            """include_service_path: [ source_path: ${sourcePath}, 
            |service_path_name: ${servicePathName}, 
            |path_item.description: ${pathItem.description} 
            |]""".flatten()
        )
        return filters.any { filter ->
            filter.includeServicePath(sourcePath, servicePathName, pathItem)
        }
    }
}
