package funcify.feature.datasource.rest.swagger

import arrow.core.identity
import arrow.core.toOption
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatTypes
import funcify.feature.datasource.rest.error.RestApiDataSourceException
import funcify.feature.datasource.rest.error.RestApiErrorResponse
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.flatMapOptions
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentSetValueMap
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import kotlinx.collections.immutable.PersistentMap
import org.slf4j.Logger
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

/**
 *
 * @author smccarron
 * @created 2022-07-11
 */
interface SwaggerV3ParserSourceIndexCreationTraversalTemplate<WT> :
    SwaggerV3ParserTraversalTemplate<WT>,
    SwaggerV3ParserSourceIndexCreationTemplate<WT>,
    SwaggerV3ParserSourceIndexContextMutationTemplate<WT> {

    companion object {
        private val logger: Logger =
            loggerFor<SwaggerV3ParserSourceIndexCreationTraversalTemplate<*>>()
    }

    override fun onOpenAPI(
        openAPIRepresentation: OpenAPI,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT> {
        logger.debug(
            "on_open_api: [ open_api_representation.open_api: ${openAPIRepresentation.openapi} ]"
        )
        val childPathPathItemPairsByParentPath:
            PersistentMap<SchematicPath, PersistentMap<SchematicPath, PathItem>> =
            openAPIRepresentation
                .toOption()
                .mapNotNull { o -> o.paths }
                .mapNotNull { p ->
                    p.asSequence().map { (pathString, pathItem) ->
                        Pair(SchematicPath.of { pathSegments(pathString.split('/')) }, pathItem)
                    }
                }
                .fold(::emptySequence, ::identity)
                .map { (sp, pi) -> sp.getParentPath().map { pp -> pp to (sp to pi) } }
                .flatMapOptions()
                .reducePairsToPersistentSetValueMap()
                .asSequence()
                .map { (parentPath, childPathPathItemPairsSet) ->
                    parentPath to childPathPathItemPairsSet.stream().reducePairsToPersistentMap()
                }
                .reducePairsToPersistentMap()
        return childPathPathItemPairsByParentPath.asSequence().fold(contextContainer) {
            ctxCont: SwaggerSourceIndexContextContainer<WT>,
            (parentPath: SchematicPath, pathItemByChildPath: PersistentMap<SchematicPath, PathItem>)
            ->
            onServicePathsGroup(parentPath, pathItemByChildPath, ctxCont)
        }
    }

    override fun onServicePathsGroup(
        parentPath: SchematicPath,
        pathInfoBySchematicPath: Map<SchematicPath, PathItem>,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT> {
        logger.debug(
            """on_service_paths_group: [ parent_path: ${parentPath}, 
            |path_info_by_schematic_path.size: ${pathInfoBySchematicPath.size} 
            |]""".flattenIntoOneLine()
        )
        return createSourceContainerTypeInContextForPathsGroup(
                parentPath,
                pathInfoBySchematicPath,
                contextContainer
            )
            .let { updatedContext ->
                pathInfoBySchematicPath.asSequence().fold(updatedContext) {
                    updCtx,
                    (childPath, pathInfo) ->
                    onServicePath(childPath, pathInfo, updCtx)
                }
            }
    }

    override fun onServicePath(
        sourcePath: SchematicPath,
        pathInfo: PathItem,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT> {
        logger.debug(
            "on_service_path: [ source_path: ${sourcePath}, path_info: ${pathInfo.description} ]"
        )
        return when (val postOperation: Operation? = pathInfo.post) {
            null -> {
                contextContainer
            }
            else -> {
                postOperation.responses
                    .toOption()
                    .flatMap { apiResponses: ApiResponses ->
                        apiResponses
                            .asSequence()
                            .filter { (status, _) ->
                                status.matches(Regex("\\d\\d\\d")) &&
                                    HttpStatus.resolve(status.toInt())?.is2xxSuccessful ?: false
                            }
                            .firstOrNull()
                            .toOption()
                    }
                    .map { (_, apiResponse) -> apiResponse }
                    .fold(
                        { contextContainer },
                        { apiResponse ->
                            onSuccessfulPostResponse(sourcePath, apiResponse, contextContainer)
                        }
                    )
                    .let { context ->
                        postOperation
                            .toOption()
                            .mapNotNull { op -> op.requestBody }
                            .fold(
                                { context },
                                { requestBody: RequestBody ->
                                    onServicePostRequest(sourcePath, requestBody, context)
                                }
                            )
                    }
            }
        }
    }

    override fun onServicePostRequest(
        sourcePath: SchematicPath,
        request: RequestBody,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT> {
        val requestContentSize = request.content.toOption().mapNotNull { c -> c.size }.orNull() ?: 0
        logger.debug(
            """on_service_post_request: [ source_path: ${sourcePath}, 
            |request.content.size: $requestContentSize ]
            |""".flattenIntoOneLine()
        )
        return request.content
            .toOption()
            .mapNotNull { content: Content ->
                content
                    .asSequence()
                    .filter { (name, _) -> name == MediaType.APPLICATION_JSON_VALUE }
                    .firstOrNull()
            }
            .mapNotNull { (_, mediaTypeInfo) -> mediaTypeInfo.schema }
            .fold(
                { contextContainer },
                { schema: Schema<*> ->
                    onServicePostRequestJsonSchema(sourcePath, request, schema, contextContainer)
                }
            )
    }

    override fun onServicePostRequestJsonSchema(
        sourcePath: SchematicPath,
        request: RequestBody,
        requestBodySchema: Schema<*>,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT> {
        val methodTag: String = "on_service_post_request_json_schema"
        logger.debug(
            """$methodTag: [ source_path: ${sourcePath}, 
            |request_body_schema.type: ${requestBodySchema.type} 
            |]""".flattenIntoOneLine()
        )
        return when (val requestBodySchemaType = JsonFormatTypes.forValue(requestBodySchema.type)) {
            JsonFormatTypes.OBJECT -> {
                createParameterContainerTypeForPostRequestBodyObject(
                        sourcePath,
                        request,
                        requestBodySchema,
                        contextContainer
                    )
                    .let { updatedContext ->
                        requestBodySchema.properties
                            .toOption()
                            .mapNotNull { propertySchemaByPropName ->
                                propertySchemaByPropName.asSequence()
                            }
                            .fold(::emptySequence, ::identity)
                            .fold(updatedContext) { ctx, (propertyName, propertySchema) ->
                                onServicePostRequestJsonSchemaProperty(
                                    sourcePath.transform { argument(propertyName) },
                                    request,
                                    requestBodySchema,
                                    propertyName,
                                    propertySchema,
                                    ctx
                                )
                            }
                    }
            }
            else -> {
                val errorMessage: String =
                    """post_request_body.type is not an object type 
                       |and thus requires handling 
                       |that is not implemented: 
                       |[ actual: ${requestBodySchemaType} ]""".flattenIntoOneLine()
                logger.error(
                    """$methodTag: [ status: failed ] 
                    |${errorMessage}""".flattenIntoOneLine()
                )
                throw RestApiDataSourceException(
                    RestApiErrorResponse.REST_API_DATA_SOURCE_CREATION_ERROR,
                    errorMessage
                )
            }
        }
    }

    override fun onServicePostRequestJsonSchemaProperty(
        sourcePath: SchematicPath,
        request: RequestBody,
        requestBodySchema: Schema<*>,
        requestBodyPropertyName: String,
        requestBodyPropertySchema: Schema<*>,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT> {
        val methodTag: String = "on_service_post_request_json_schema_property"
        logger.debug(
            """$methodTag: [ source_path: $sourcePath, 
            |request_body_property_name: $requestBodyPropertyName, 
            |request_body_property_schema.type: ${requestBodyPropertySchema.type} 
            |]""".flattenIntoOneLine()
        )
        return createParameterAttributeForPostRequestBodyObjectProperty(
            sourcePath,
            request,
            requestBodySchema,
            requestBodyPropertyName,
            requestBodyPropertySchema,
            contextContainer
        )
    }

    override fun onSuccessfulPostResponse(
        sourcePath: SchematicPath,
        response: ApiResponse,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT> {
        val firstMediaTypeNameInContent: String =
            response.content
                .toOption()
                .filter { c: Content -> c.size > 0 }
                .mapNotNull { c: Content -> c.asSequence().firstOrNull() }
                .mapNotNull { (mediaTypeName, _) -> mediaTypeName }
                .orNull()
                ?: "<NA>"
        logger.debug(
            """on_successful_post_response: [ source_path: ${sourcePath}, 
            |api_response.first.media_type_name: ${firstMediaTypeNameInContent} 
            |]""".flattenIntoOneLine()
        )
        return response.content
            .toOption()
            .mapNotNull { c: Content ->
                c.asSequence()
                    .filter { (name, _) -> name == MediaType.APPLICATION_JSON_VALUE }
                    .mapNotNull { (_, mediaTypeInfo) -> mediaTypeInfo }
                    .firstOrNull()
            }
            .mapNotNull { mediaType: io.swagger.v3.oas.models.media.MediaType -> mediaType.schema }
            .fold(
                { contextContainer },
                { responseJsonSchema: Schema<*> ->
                    onPostResponseJsonSchema(
                        sourcePath,
                        response,
                        responseJsonSchema,
                        contextContainer
                    )
                }
            )
    }

    override fun onPostResponseJsonSchema(
        sourcePath: SchematicPath,
        response: ApiResponse,
        responseBodyJson: Schema<*>,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT> {
        val methodTag: String = "on_post_response_json_schema"
        logger.debug(
            """$methodTag: [ source_path: $sourcePath, 
                |response_body_json_schema.type: ${responseBodyJson.type} 
                |]""".flattenIntoOneLine()
        )
        when (val responseBodyJsonType = JsonFormatTypes.forValue(responseBodyJson.type)) {
            JsonFormatTypes.OBJECT -> {
                TODO("Not yet implemented")
            }
            else -> {
                TODO("Not yet implemented")
            }
        }
    }

    override fun onPostResponseJsonSchemaProperty(
        sourcePath: SchematicPath,
        response: ApiResponse,
        responseBodyJson: Schema<*>,
        responseBodyObjectPropertyName: String,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT> {
        TODO("Not yet implemented")
    }
}
