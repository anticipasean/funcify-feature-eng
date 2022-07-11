package funcify.feature.datasource.rest.swagger

import funcify.feature.schema.path.SchematicPath

/**
 *
 * @author smccarron
 * @created 2022-07-10
 */
interface SwaggerOpenAPISchemaTraversalTemplate<WT, O, P, REQ, RES, SCH> {

    fun onOpenAPI(
        openAPIRepresentation: O,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): SwaggerSourceIndexContextContainer<WT>

    fun onServicePathsGroup(
        parentPath: SchematicPath,
        pathInfoBySchematicPath: Map<SchematicPath, P>,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): SwaggerSourceIndexContextContainer<WT>

    fun onServicePath(
        sourcePath: SchematicPath,
        pathInfo: P,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): SwaggerSourceIndexContextContainer<WT>

    fun onServicePostRequest(
        sourcePath: SchematicPath,
        request: REQ,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): SwaggerSourceIndexContextContainer<WT>

    fun onServicePostRequestJsonSchema(
        sourcePath: SchematicPath,
        request: REQ,
        requestBodySchema: SCH,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): SwaggerSourceIndexContextContainer<WT>

    fun onServicePostRequestJsonSchemaProperty(
        sourcePath: SchematicPath,
        request: REQ,
        requestBodySchema: SCH,
        requestBodyPropertyName: String,
        requestBodyPropertySchema: SCH,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): SwaggerSourceIndexContextContainer<WT>

    fun onSuccessfulPostResponse(
        sourcePath: SchematicPath,
        response: RES,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): SwaggerSourceIndexContextContainer<WT>

    fun onPostResponseJsonSchema(
        sourcePath: SchematicPath,
        response: RES,
        responseBodyJson: SCH,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): SwaggerSourceIndexContextContainer<WT>

    fun onPostResponseJsonSchemaProperty(
        sourcePath: SchematicPath,
        response: RES,
        responseBodyJson: SCH,
        responseBodyObjectPropertyName: String,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): SwaggerSourceIndexContextContainer<WT>
}
