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
 * @created 2022-07-10
 */
interface SwaggerV3ParserTraversalTemplate<WT> :
    SwaggerOpenAPISchemaTraversalTemplate<
        WT, OpenAPI, PathItem, RequestBody, ApiResponse, Schema<*>> {

    override fun onOpenAPI(
        openAPIRepresentation: OpenAPI,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT>

    override fun onServicePathsGroup(
        parentPath: SchematicPath,
        pathInfoBySchematicPath: Map<SchematicPath, PathItem>,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT>

    override fun onServicePath(
        sourcePath: SchematicPath,
        pathInfo: PathItem,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT>

    override fun onServicePostRequest(
        sourcePath: SchematicPath,
        request: RequestBody,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT>

    override fun onServicePostRequestJsonSchema(
        sourcePath: SchematicPath,
        request: RequestBody,
        requestBodySchema: Schema<*>,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT>

    override fun onServicePostRequestJsonSchemaProperty(
        sourcePath: SchematicPath,
        request: RequestBody,
        requestBodySchema: Schema<*>,
        requestBodyPropertyName: String,
        requestBodyPropertySchema: Schema<*>,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT>

    override fun onSuccessfulPostResponse(
        sourcePath: SchematicPath,
        response: ApiResponse,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT>

    override fun onPostResponseJsonSchema(
        sourcePath: SchematicPath,
        response: ApiResponse,
        responseBodyJson: Schema<*>,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT>

    override fun onPostResponseJsonSchemaProperty(
        sourcePath: SchematicPath,
        response: ApiResponse,
        responseBodyJson: Schema<*>,
        responseBodyObjectPropertyName: String,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT>
}
