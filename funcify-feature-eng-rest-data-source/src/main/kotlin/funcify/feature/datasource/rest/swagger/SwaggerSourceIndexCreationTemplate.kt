package funcify.feature.datasource.rest.swagger

import funcify.feature.schema.path.SchematicPath

/**
 *
 * @author smccarron
 * @created 2022-07-11
 */
interface SwaggerSourceIndexCreationTemplate<WT, O, P, REQ, RES, SCH> {

    fun createSourceContainerTypeInContextForPathsGroup(
        sourcePath: SchematicPath,
        pathsGroup: Map<SchematicPath, P>,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): SwaggerSourceIndexContextContainer<WT>

    fun createSourceAttributeInContextForPathInfoRepresentation(
        sourcePath: SchematicPath,
        pathInfo: P,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): SwaggerSourceIndexContextContainer<WT>

    fun createSourceContainerTypeInContextForSuccessfulApiResponseObject(
        sourcePath: SchematicPath,
        successfulApiResponse: RES,
        responseJsonSchema: SCH,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): SwaggerSourceIndexContextContainer<WT>

    fun createSourceAttributeInContextForPropertyOfSuccessfulApiResponseObject(
        sourcePath: SchematicPath,
        successfulApiResponse: RES,
        responseJsonSchema: SCH,
        jsonPropertyName: String,
        jsonPropertySchema: SCH,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): SwaggerSourceIndexContextContainer<WT>

    fun createParameterContainerTypeForPostRequestBodyObject(
        sourcePath: SchematicPath,
        request: REQ,
        requestBodyJsonSchema: SCH,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): SwaggerSourceIndexContextContainer<WT>

    fun createParameterAttributeForPostRequestBodyObjectProperty(
        sourcePath: SchematicPath,
        request: REQ,
        requestBodyParentSchema: SCH,
        jsonPropertyName: String,
        jsonPropertySchema: SCH,
        contextContainer: SwaggerSourceIndexContextContainer<WT>
    ): SwaggerSourceIndexContextContainer<WT>
}
