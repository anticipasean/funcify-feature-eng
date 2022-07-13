package funcify.feature.datasource.rest.swagger

import arrow.core.Option
import funcify.feature.datasource.rest.schema.RestApiSourceIndex
import funcify.feature.datasource.rest.schema.SwaggerParameterAttribute
import funcify.feature.datasource.rest.schema.SwaggerParameterContainerType
import funcify.feature.datasource.rest.schema.SwaggerRestApiSourceIndex
import funcify.feature.datasource.rest.schema.SwaggerSourceAttribute
import funcify.feature.datasource.rest.schema.SwaggerSourceContainerType
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse

/**
 *
 * @author smccarron
 * @created 2022-07-11
 */
interface SwaggerV3ParserSourceIndexContextMutationTemplate<WT> :
    SwaggerSourceIndexContextMutationTemplate<
        WT, OpenAPI, PathItem, RequestBody, ApiResponse, Schema<*>> {

    override fun getDataSourceKeyForSwaggerSourceIndicesInContext(
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): DataSource.Key<RestApiSourceIndex>

    override fun getOpenAPIRepresentationInContext(
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): OpenAPI

    override fun addNewOrUpdateExistingSwaggerSourceIndexToContext(
        newSwaggerSourceIndex: SwaggerRestApiSourceIndex,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): SwaggerSourceIndexContextContainer<WT>

    override fun getExistingSwaggerSourceContainerTypeForSchematicPath(
        sourcePath: SchematicPath,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): Option<SwaggerSourceContainerType>

    override fun getExistingSwaggerSourceAttributeForSchematicPath(
        sourcePath: SchematicPath,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): Option<SwaggerSourceAttribute>

    override fun getExistingSwaggerParameterContainerTypeForSchematicPath(
        sourcePath: SchematicPath,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): Option<SwaggerParameterContainerType>

    override fun getExistingSwaggerParameterAttributeForSchematicPath(
        sourcePath: SchematicPath,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): Option<SwaggerParameterAttribute>
}
