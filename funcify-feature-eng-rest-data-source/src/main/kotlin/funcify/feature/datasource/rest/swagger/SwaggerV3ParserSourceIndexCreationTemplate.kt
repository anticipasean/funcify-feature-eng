package funcify.feature.datasource.rest.swagger

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
interface SwaggerV3ParserSourceIndexCreationTemplate<WT> :
    SwaggerSourceIndexCreationTemplate<WT, OpenAPI, PathItem, RequestBody, ApiResponse, Schema<*>> {

    override fun createSourceIndicesInContextForPathsGroup(
        sourcePath: SchematicPath,
        pathsGroup: Map<SchematicPath, Pair<String, PathItem>>,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): SwaggerSourceIndexContextContainer<WT>

    override fun createSourceAttributeInContextForPathInfoRepresentation(
        sourcePath: SchematicPath,
        servicePathName: String,
        pathInfo: PathItem,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT>

    override fun createSourceContainerTypeInContextForSuccessfulApiResponseObject(
        sourcePath: SchematicPath,
        successfulApiResponse: ApiResponse,
        responseJsonSchema: Schema<*>,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT>

    override fun createSourceAttributeInContextForPropertyOfSuccessfulApiResponseObject(
        sourcePath: SchematicPath,
        successfulApiResponse: ApiResponse,
        responseJsonSchema: Schema<*>,
        jsonPropertyName: String,
        jsonPropertySchema: Schema<*>,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT>

    override fun createParameterContainerTypeForPostRequestBodyObject(
        sourcePath: SchematicPath,
        request: RequestBody,
        requestBodyJsonSchema: Schema<*>,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT>

    override fun createParameterAttributeForPostRequestBodyObjectProperty(
        sourcePath: SchematicPath,
        request: RequestBody,
        requestBodyParentSchema: Schema<*>,
        jsonPropertyName: String,
        jsonPropertySchema: Schema<*>,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT>
}
