package funcify.feature.datasource.rest.swagger

import org.springframework.http.HttpMethod

/**
 *
 * @author smccarron
 * @created 2022-07-10
 */
interface SwaggerOpenAPISchemaSourceIndexTemplate<WT> {

    fun <O, P, REQ, RES> getServicePathsGroupsFromOpenAPI(
        contextContainer: SwaggerSourceIndexContextContainer<WT, O, P, REQ, RES>
    ): Map<String, Map<String, P>>

    fun <O, P, REQ, RES> getRequestTypesForPath(
        pathAsString: String,
        contextContainer: SwaggerSourceIndexContextContainer<WT, O, P, REQ, RES>
    ): List<Pair<HttpMethod, REQ>>

    fun <O, P, REQ, RES> getSuccessfulResponseTypesForRequestTypeAtPath(
        httpMethod: HttpMethod,
        pathAsString: String,
        contextContainer: SwaggerSourceIndexContextContainer<WT, O, P, REQ, RES>
    ): List<RES>

    fun <O, P, REQ, RES> onServicePathsGroup(
        pathInfoByPathString: Map<String, P>,
        contextContainer: SwaggerSourceIndexContextContainer<WT, O, P, REQ, RES>
    ): SwaggerSourceIndexContextContainer<WT, O, P, REQ, RES>

    fun <O, P, REQ, RES> onServicePath(
        pathAsString: String,
        pathInfo: P,
        contextContainer: SwaggerSourceIndexContextContainer<WT, O, P, REQ, RES>
    ): SwaggerSourceIndexContextContainer<WT, O, P, REQ, RES>

    fun <O, P, REQ, RES> onServicePostRequest(
        pathAsString: String,
        pathInfo: P,
        request: REQ,
        contextContainer: SwaggerSourceIndexContextContainer<WT, O, P, REQ, RES>
    ): SwaggerSourceIndexContextContainer<WT, O, P, REQ, RES>

    fun <O, P, REQ, RES> onSuccessfulPostResponse(
        pathAsString: String,
        pathInfo: P,
        response: RES,
        contextContainer: SwaggerSourceIndexContextContainer<WT, O, P, REQ, RES>
    ): SwaggerSourceIndexContextContainer<WT, O, P, REQ, RES>

    fun <O, P, REQ, RES> createSourceContainerTypeInContextForPathsGroup(
        pathsGroup: Map<String, P>,
        contextContainer: SwaggerSourceIndexContextContainer<WT, O, P, REQ, RES>
    ): SwaggerSourceIndexContextContainer<WT, O, P, REQ, RES>

    fun <O, P, REQ, RES> createSourceContainerTypeInContextForSuccessfulApiResponseObject(
        successfulApiResponse: RES,
        contextContainer: SwaggerSourceIndexContextContainer<WT, O, P, REQ, RES>
    ): SwaggerSourceIndexContextContainer<WT, O, P, REQ, RES>

    fun <O, P, REQ, RES> createSourceAttributeInContextForPropertyOfSuccessfulApiResponseObject(
        pathAsString: String,
        pathInfo: P,
        successfulApiResponse: RES,
        contextContainer: SwaggerSourceIndexContextContainer<WT, O, P, REQ, RES>
    ): SwaggerSourceIndexContextContainer<WT, O, P, REQ, RES>

    fun <O, P, REQ, RES> createParameterContainerTypeForPostRequestBodyObject(
        pathAsString: String,
        pathInfo: P,
        request: REQ,
        contextContainer: SwaggerSourceIndexContextContainer<WT, O, P, REQ, RES>
    ): SwaggerSourceIndexContextContainer<WT, O, P, REQ, RES>

    fun <O, P, REQ, RES> createParameterAttributeForPostRequestBodyObjectProperty(
        pathAsString: String,
        pathInfo: P,
        request: REQ,
        jsonPropertyName: String,
        contextContainer: SwaggerSourceIndexContextContainer<WT, O, P, REQ, RES>
    ): SwaggerSourceIndexContextContainer<WT, O, P, REQ, RES>
}
