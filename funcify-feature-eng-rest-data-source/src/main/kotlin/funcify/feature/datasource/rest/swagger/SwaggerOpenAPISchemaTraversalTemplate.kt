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
        childNameAndPathInfoBySchematicPath: Map<SchematicPath, Pair<String, P>>,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): SwaggerSourceIndexContextContainer<WT>

    fun onServicePath(
        sourcePath: SchematicPath,
        servicePathName: String,
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
        requestBodyJsonSchema: SCH,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): SwaggerSourceIndexContextContainer<WT>

    fun onServicePostRequestJsonSchemaProperty(
        sourcePath: SchematicPath,
        request: REQ,
        requestBodyJsonSchema: SCH,
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
        responseBodyJsonSchema: SCH,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): SwaggerSourceIndexContextContainer<WT>

    fun onPostResponseJsonSchemaProperty(
        sourcePath: SchematicPath,
        response: RES,
        responseBodyJsonSchema: SCH,
        jsonPropertyName: String,
        jsonPropertySchema: SCH,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): SwaggerSourceIndexContextContainer<WT>
}
