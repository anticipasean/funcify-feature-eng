package funcify.feature.datasource.rest.metadata.filter

import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
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

    override fun includeServicePath(sourcePath: SchematicPath, pathItem: PathItem): Boolean {
        return filters.any { filter -> filter.includeServicePath(sourcePath, pathItem) }
    }
}
