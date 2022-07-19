package funcify.feature.datasource.rest.swagger

import arrow.core.Option
import arrow.core.toOption
import funcify.feature.datasource.rest.error.RestApiDataSourceException
import funcify.feature.datasource.rest.error.RestApiErrorResponse
import funcify.feature.datasource.rest.schema.RestApiSourceIndex
import funcify.feature.datasource.rest.schema.SwaggerParameterAttribute
import funcify.feature.datasource.rest.schema.SwaggerParameterContainerType
import funcify.feature.datasource.rest.schema.SwaggerRestApiSourceIndex
import funcify.feature.datasource.rest.schema.SwaggerSourceAttribute
import funcify.feature.datasource.rest.schema.SwaggerSourceContainerType
import funcify.feature.datasource.rest.swagger.SwaggerV3ParserSourceIndexContext.Companion.SV3PWT
import funcify.feature.datasource.rest.swagger.SwaggerV3ParserSourceIndexContext.Companion.narrowed
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.PathItem

/**
 *
 * @author smccarron
 * @created 2022-07-12
 */
internal class DefaultSwaggerV3ParserSourceIndexFactory() : SwaggerV3ParserSourceIndexFactory {

    override fun getDataSourceKeyForSwaggerSourceIndicesInContext(
        contextContainer: SwaggerSourceIndexContextContainer<SV3PWT>
    ): DataSource.Key<RestApiSourceIndex> {
        return contextContainer.narrowed().swaggerAPIDataSourceKey
    }

    override fun getOpenAPIRepresentationInContext(
        contextContainer: SwaggerSourceIndexContextContainer<SV3PWT>
    ): OpenAPI {
        return contextContainer.narrowed().openAPI
    }

    override fun addNewOrUpdateExistingSwaggerSourceIndexToContext(
        newSwaggerSourceIndex: SwaggerRestApiSourceIndex,
        contextContainer: SwaggerSourceIndexContextContainer<SV3PWT>,
    ): SwaggerSourceIndexContextContainer<SV3PWT> {
        return when (newSwaggerSourceIndex) {
            is SwaggerSourceContainerType -> {
                contextContainer.narrowed().update {
                    addSourceContainerTypeForPath(
                        newSwaggerSourceIndex.sourcePath,
                        newSwaggerSourceIndex
                    )
                }
            }
            is SwaggerSourceAttribute -> {
                contextContainer.narrowed().update {
                    addSourceAttributeForPath(
                        newSwaggerSourceIndex.sourcePath,
                        newSwaggerSourceIndex
                    )
                }
            }
            is SwaggerParameterContainerType -> {
                contextContainer.narrowed().update {
                    addParameterContainerTypeForPath(
                        newSwaggerSourceIndex.sourcePath,
                        newSwaggerSourceIndex
                    )
                }
            }
            is SwaggerParameterAttribute -> {
                contextContainer.narrowed().update {
                    addParameterAttributeForPath(
                        newSwaggerSourceIndex.sourcePath,
                        newSwaggerSourceIndex
                    )
                }
            }
            else -> {
                throw RestApiDataSourceException(
                    RestApiErrorResponse.UNEXPECTED_ERROR,
                    """unhandled swagger_rest_api_index type submitted to be 
                        |added to context: [ 
                        |actual: ${newSwaggerSourceIndex::class.qualifiedName} 
                        |]""".flattenIntoOneLine()
                )
            }
        }
    }

    override fun getExistingSwaggerSourceContainerTypeForSchematicPath(
        sourcePath: SchematicPath,
        contextContainer: SwaggerSourceIndexContextContainer<SV3PWT>,
    ): Option<SwaggerSourceContainerType> {
        return contextContainer
            .narrowed()
            .sourceContainerTypesBySchematicPath[sourcePath]
            .toOption()
    }

    override fun getExistingSwaggerSourceAttributeForSchematicPath(
        sourcePath: SchematicPath,
        contextContainer: SwaggerSourceIndexContextContainer<SV3PWT>,
    ): Option<SwaggerSourceAttribute> {
        return contextContainer.narrowed().sourceAttributesBySchematicPath[sourcePath].toOption()
    }

    override fun getExistingSwaggerParameterContainerTypeForSchematicPath(
        sourcePath: SchematicPath,
        contextContainer: SwaggerSourceIndexContextContainer<SV3PWT>,
    ): Option<SwaggerParameterContainerType> {
        return contextContainer
            .narrowed()
            .parameterContainerTypesBySchematicPath[sourcePath]
            .toOption()
    }

    override fun getExistingSwaggerParameterAttributeForSchematicPath(
        sourcePath: SchematicPath,
        contextContainer: SwaggerSourceIndexContextContainer<SV3PWT>,
    ): Option<SwaggerParameterAttribute> {
        return contextContainer.narrowed().parameterAttributesBySchematicPath[sourcePath].toOption()
    }

    override fun shouldIncludeSourcePathAndPathInfo(
        sourcePath: SchematicPath,
        pathInfo: PathItem,
        contextContainer: SwaggerSourceIndexContextContainer<SV3PWT>,
    ): Boolean {
        return contextContainer
            .narrowed()
            .swaggerRestApiSourceMetadataFilter
            .includeServicePath(sourcePath, pathInfo)
    }
}
