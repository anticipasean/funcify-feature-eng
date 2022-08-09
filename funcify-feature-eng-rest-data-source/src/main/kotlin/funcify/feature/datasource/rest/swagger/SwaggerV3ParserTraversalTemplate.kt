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
        childNameAndPathInfoBySchematicPath: Map<SchematicPath, Pair<String, PathItem>>,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): SwaggerSourceIndexContextContainer<WT>

    override fun onServicePath(
        sourcePath: SchematicPath,
        servicePathName: String,
        pathInfo: PathItem,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): SwaggerSourceIndexContextContainer<WT>

    override fun onServicePostRequest(
        sourcePath: SchematicPath,
        request: RequestBody,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT>

    override fun onServicePostRequestJsonSchema(
        sourcePath: SchematicPath,
        request: RequestBody,
        requestBodyJsonSchema: Schema<*>,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT>

    override fun onServicePostRequestJsonSchemaProperty(
        sourcePath: SchematicPath,
        request: RequestBody,
        requestBodyJsonSchema: Schema<*>,
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
        responseBodyJsonSchema: Schema<*>,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT>

    override fun onPostResponseJsonSchemaProperty(
        sourcePath: SchematicPath,
        response: ApiResponse,
        responseBodyJsonSchema: Schema<*>,
        jsonPropertyName: String,
        jsonPropertySchema: Schema<*>,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT>
}
