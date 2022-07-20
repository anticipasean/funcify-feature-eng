package funcify.feature.datasource.rest.swagger

import arrow.core.getOrElse
import arrow.core.identity
import arrow.core.lastOrNone
import arrow.core.some
import arrow.core.toOption
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatTypes
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import funcify.feature.datasource.rest.error.RestApiDataSourceException
import funcify.feature.datasource.rest.error.RestApiErrorResponse
import funcify.feature.datasource.rest.naming.RestApiSourceNamingConventions
import funcify.feature.datasource.rest.schema.DefaultSwaggerParameterAttribute
import funcify.feature.datasource.rest.schema.DefaultSwaggerParameterContainerType
import funcify.feature.datasource.rest.schema.DefaultSwaggerPathGroupSourceContainerType
import funcify.feature.datasource.rest.schema.DefaultSwaggerResponseTypeSourceContainerType
import funcify.feature.datasource.rest.schema.DefaultSwaggerSourcePathItemAttribute
import funcify.feature.datasource.rest.schema.DefaultSwaggerSourcePathItemGroupAttribute
import funcify.feature.datasource.rest.schema.DefaultSwaggerSourceResponseBodyPropertyAttribute
import funcify.feature.naming.ConventionalName
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.extensions.JsonNodeExtensions.addChildFieldAndValuePairToRightmostTreeNode
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.flatMapOptions
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentSetValueMap
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
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
        childPathInfoBySchematicPath: Map<SchematicPath, PathItem>,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT> {
        logger.debug(
            """on_service_paths_group: [ parent_path: ${parentPath}, 
            |path_info_by_schematic_path.size: ${childPathInfoBySchematicPath.size} 
            |]""".flattenIntoOneLine()
        )
        return createSourceIndicesInContextForPathsGroup(
                parentPath,
                childPathInfoBySchematicPath,
                contextContainer
            )
            .let { updatedContext ->
                childPathInfoBySchematicPath.asSequence().fold(updatedContext) {
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
        if (!shouldIncludeSourcePathAndPathInfo(sourcePath, pathInfo, contextContainer)) {
            logger.debug("on_service_path: [ excluding source_path: $sourcePath ]")
            return contextContainer
        }
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
                            onSuccessfulPostResponse(
                                sourcePath,
                                apiResponse,
                                createSourceAttributeInContextForPathInfoRepresentation(
                                    sourcePath,
                                    pathInfo,
                                    contextContainer
                                )
                            )
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
            .flatMap { requestBodyJsonSchema: Schema<*> ->
                if (requestBodyJsonSchema.type == null && requestBodyJsonSchema.`$ref` != null) {
                    val openAPIRep: OpenAPI = getOpenAPIRepresentationInContext(contextContainer)
                    requestBodyJsonSchema.`$ref`
                        .split('/')
                        .toOption()
                        .filter { pathSegments -> pathSegments.size > 1 }
                        .flatMap { pathSegments -> pathSegments.lastOrNone() }
                        .filter { lastSegment ->
                            lastSegment in (openAPIRep.components?.schemas ?: emptyMap())
                        }
                        .mapNotNull { lastSegment -> openAPIRep.components.schemas[lastSegment] }
                } else {
                    requestBodyJsonSchema.toOption()
                }
            }
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
        requestBodyJsonSchema: Schema<*>,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT> {
        val methodTag: String = "on_service_post_request_json_schema"
        logger.debug(
            """$methodTag: [ source_path: ${sourcePath}, 
            |request_body_schema.type: ${requestBodyJsonSchema.type} 
            |]""".flattenIntoOneLine()
        )

        return when (
            val requestBodySchemaType = JsonFormatTypes.forValue(requestBodyJsonSchema.type)
        ) {
            JsonFormatTypes.OBJECT -> {
                createParameterContainerTypeForPostRequestBodyObject(
                        sourcePath,
                        request,
                        requestBodyJsonSchema,
                        contextContainer
                    )
                    .let { updatedContext ->
                        requestBodyJsonSchema.properties
                            .toOption()
                            .mapNotNull { propertySchemaByPropName ->
                                propertySchemaByPropName.asSequence()
                            }
                            .fold(::emptySequence, ::identity)
                            .fold(updatedContext) { ctx, (propertyName, propertySchema) ->
                                onServicePostRequestJsonSchemaProperty(
                                    sourcePath.transform { argument(propertyName) },
                                    request,
                                    requestBodyJsonSchema,
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
        requestBodyJsonSchema: Schema<*>,
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
        return when (JsonFormatTypes.forValue(requestBodyPropertySchema?.type)) {
            JsonFormatTypes.OBJECT -> {
                createParameterContainerTypeForPostRequestBodyObject(
                        sourcePath,
                        request,
                        requestBodyPropertySchema,
                        contextContainer
                    )
                    .let { updatedContext ->
                        createParameterAttributeForPostRequestBodyObjectProperty(
                            sourcePath,
                            request,
                            requestBodyJsonSchema,
                            requestBodyPropertyName,
                            requestBodyPropertySchema,
                            updatedContext
                        )
                    }
                    .let { updatedContext ->
                        requestBodyPropertySchema
                            .toOption()
                            .mapNotNull { s: Schema<*> -> s.properties }
                            .mapNotNull { propsByName -> propsByName.asSequence() }
                            .fold(::emptySequence, ::identity)
                            .fold(updatedContext) { ctx, (propertyName, propertySchema) ->
                                sourcePath.arguments
                                    .asSequence()
                                    .lastOrNull()
                                    .toOption()
                                    .flatMap { (lastArgKey, lastArgNode) ->
                                        lastArgNode
                                            .addChildFieldAndValuePairToRightmostTreeNode(
                                                propertyName,
                                                JsonNodeFactory.instance.nullNode()
                                            )
                                            .map { updatedLastArgNode ->
                                                lastArgKey to updatedLastArgNode
                                            }
                                    }
                                    .fold(
                                        { ctx },
                                        { (lastArgKey, lastArgNode) ->
                                            onServicePostRequestJsonSchemaProperty(
                                                sourcePath.transform {
                                                    argument(lastArgKey, lastArgNode)
                                                },
                                                request,
                                                requestBodyJsonSchema,
                                                propertyName,
                                                propertySchema,
                                                ctx
                                            )
                                        }
                                    )
                            }
                    }
            }
            else -> {
                createParameterAttributeForPostRequestBodyObjectProperty(
                    sourcePath,
                    request,
                    requestBodyJsonSchema,
                    requestBodyPropertyName,
                    requestBodyPropertySchema,
                    contextContainer
                )
            }
        }
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
            .flatMap { responseBodyJsonSchema: Schema<*> ->
                if (responseBodyJsonSchema.type == null && responseBodyJsonSchema.`$ref` != null) {
                    val openAPIRep: OpenAPI = getOpenAPIRepresentationInContext(contextContainer)
                    responseBodyJsonSchema.`$ref`
                        .split('/')
                        .toOption()
                        .filter { pathSegments -> pathSegments.size > 1 }
                        .flatMap { pathSegments -> pathSegments.lastOrNone() }
                        .filter { lastSegment ->
                            lastSegment in (openAPIRep.components?.schemas ?: emptyMap())
                        }
                        .mapNotNull { lastSegment -> openAPIRep.components.schemas[lastSegment] }
                } else {
                    responseBodyJsonSchema.toOption()
                }
            }
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
        responseBodyJsonSchema: Schema<*>,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT> {
        val methodTag: String = "on_post_response_json_schema"
        logger.debug(
            """$methodTag: [ source_path: $sourcePath, 
                |response_body_json_schema.type: ${responseBodyJsonSchema.type} 
                |]""".flattenIntoOneLine()
        )
        return when (
            val responseBodyJsonType = JsonFormatTypes.forValue(responseBodyJsonSchema.type)
        ) {
            JsonFormatTypes.OBJECT -> {
                createSourceContainerTypeInContextForSuccessfulApiResponseObject(
                        sourcePath,
                        response,
                        responseBodyJsonSchema,
                        contextContainer
                    )
                    .let { updatedContext ->
                        responseBodyJsonSchema.properties
                            .toOption()
                            .mapNotNull { propsByName -> propsByName.asSequence() }
                            .fold(::emptySequence, ::identity)
                            .fold(updatedContext) { ctx, (propertyName, propertySchema) ->
                                onPostResponseJsonSchemaProperty(
                                    sourcePath.transform { pathSegment(propertyName) },
                                    response,
                                    responseBodyJsonSchema,
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
                       |[ actual: ${responseBodyJsonType} ]""".flattenIntoOneLine()
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

    override fun onPostResponseJsonSchemaProperty(
        sourcePath: SchematicPath,
        response: ApiResponse,
        responseBodyJsonSchema: Schema<*>,
        jsonPropertyName: String,
        jsonPropertySchema: Schema<*>,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT> {
        val methodTag: String = "on_post_response_json_schema_property"
        logger.debug(
            """$methodTag: [ source_path: $sourcePath, 
                |json_property_name: $jsonPropertyName, 
                |json_property_schema.type: ${jsonPropertySchema.type} 
                |]""".flattenIntoOneLine()
        )
        return when (JsonFormatTypes.forValue(jsonPropertySchema?.type)) {
            JsonFormatTypes.OBJECT -> {
                createSourceAttributeInContextForPropertyOfSuccessfulApiResponseObject(
                        sourcePath,
                        response,
                        responseBodyJsonSchema,
                        jsonPropertyName,
                        jsonPropertySchema,
                        createSourceContainerTypeInContextForSuccessfulApiResponseObject(
                            sourcePath,
                            response,
                            jsonPropertySchema,
                            contextContainer
                        )
                    )
                    .let { updatedContext ->
                        jsonPropertySchema
                            .toOption()
                            .mapNotNull { s: Schema<*> -> s.properties }
                            .mapNotNull { props -> props.asSequence() }
                            .fold(::emptySequence, ::identity)
                            .fold(updatedContext) { ctx, (propertyName, propertySchema) ->
                                onPostResponseJsonSchemaProperty(
                                    sourcePath.transform { pathSegment(propertyName) },
                                    response,
                                    responseBodyJsonSchema,
                                    propertyName,
                                    propertySchema,
                                    ctx
                                )
                            }
                    }
            }
            else -> {
                createSourceAttributeInContextForPropertyOfSuccessfulApiResponseObject(
                    sourcePath,
                    response,
                    responseBodyJsonSchema,
                    jsonPropertyName,
                    jsonPropertySchema,
                    contextContainer
                )
            }
        }
    }

    override fun createSourceIndicesInContextForPathsGroup(
        sourcePath: SchematicPath,
        pathsGroup: Map<SchematicPath, PathItem>,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT> {
        val pathsGroupFirstPath: String =
            pathsGroup
                .toOption()
                .mapNotNull { childPathItemBySchematicPath ->
                    childPathItemBySchematicPath.asSequence().firstOrNull()
                }
                .mapNotNull { (path, _) -> path }
                .mapNotNull { sp -> sp.toString() }
                .getOrElse { "<NA>" }
        logger.debug(
            """create_source_indices_in_in_context_for_paths_group: 
                |[ source_path: $sourcePath, 
                |paths_group.size: ${pathsGroup.size}, 
                |paths_group.first.path: $pathsGroupFirstPath 
                |]""".flattenIntoOneLine()
        )
        val conventionalName: ConventionalName =
            if (sourcePath.isRoot()) {
                RestApiSourceNamingConventions
                    .getPathGroupTypeNamingConventionForPathGroupPathName()
                    .deriveName(
                        getDataSourceKeyForSwaggerSourceIndicesInContext(contextContainer).name
                    )
            } else {
                sourcePath.pathSegments
                    .lastOrNone()
                    .successIfDefined {
                        RestApiDataSourceException(
                            RestApiErrorResponse.REST_API_DATA_SOURCE_CREATION_ERROR,
                            """path_group.source_path.path_segments 
                            |does not contain a name that can be 
                            |used for this type name; information about 
                            |the root may need to be provided
                            |""".flattenIntoOneLine()
                        )
                    }
                    .map { lastSegment ->
                        RestApiSourceNamingConventions
                            .getPathGroupTypeNamingConventionForPathGroupPathName()
                            .deriveName(lastSegment)
                    }
                    .orElseThrow()
            }
        return addNewOrUpdateExistingSwaggerSourceIndexToContext(
                DefaultSwaggerPathGroupSourceContainerType(
                    getDataSourceKeyForSwaggerSourceIndicesInContext(contextContainer),
                    sourcePath,
                    conventionalName,
                    pathsGroup.toPersistentMap()
                ),
                contextContainer
            )
            .let { updatedContext ->
                if (!sourcePath.isRoot()) {
                    val attributeConventionalName =
                        sourcePath.pathSegments
                            .lastOrNone()
                            .successIfDefined {
                                RestApiDataSourceException(
                                    RestApiErrorResponse.REST_API_DATA_SOURCE_CREATION_ERROR,
                                    """path_group.source_path.path_segments 
                                       |does not contain a name that can be 
                                       |used for this type name; information about 
                                       |the root may need to be provided
                                       |""".flattenIntoOneLine()
                                )
                            }
                            .map { lastSegment ->
                                RestApiSourceNamingConventions.getFieldNamingConventionForPathName()
                                    .deriveName(lastSegment)
                            }
                            .orElseThrow()
                    addNewOrUpdateExistingSwaggerSourceIndexToContext(
                        DefaultSwaggerSourcePathItemGroupAttribute(
                            sourcePath,
                            getDataSourceKeyForSwaggerSourceIndicesInContext(updatedContext),
                            attributeConventionalName
                        ),
                        updatedContext
                    )
                } else {
                    updatedContext
                }
            }
            .let { updatedContext ->
                if (
                    sourcePath.getParentPath().isDefined() &&
                        !sourcePath
                            .getParentPath()
                            .flatMap { pp ->
                                getExistingSwaggerSourceContainerTypeForSchematicPath(
                                    pp,
                                    updatedContext
                                )
                            }
                            .isDefined()
                ) {
                    sourcePath
                        .getParentPath()
                        .fold(
                            { updatedContext },
                            { pp ->
                                createSourceIndicesInContextForPathsGroup(
                                    pp,
                                    persistentMapOf(),
                                    updatedContext
                                )
                            }
                        )
                } else {
                    updatedContext
                }
            }
    }

    override fun createSourceContainerTypeInContextForSuccessfulApiResponseObject(
        sourcePath: SchematicPath,
        successfulApiResponse: ApiResponse,
        responseJsonSchema: Schema<*>,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT> {
        val responseJsonSchemaPropertiesSize: Int =
            responseJsonSchema.properties.toOption().mapNotNull { m -> m.size }.getOrElse { 0 }
        logger.debug(
            """create_source_container_type_in_context_for_successful_api_response_object: 
                |[ source_path: ${sourcePath}, 
                |response_json_schema.type: ${responseJsonSchema.type}, 
                |response_json_schema.properties.size: $responseJsonSchemaPropertiesSize ]
                |""".flattenIntoOneLine()
        )
        val conventionalName: ConventionalName =
            sourcePath.pathSegments
                .lastOrNone()
                .successIfDefined {
                    RestApiDataSourceException(
                        RestApiErrorResponse.REST_API_DATA_SOURCE_CREATION_ERROR,
                        """response_type.source_path.path_segments 
                            |does not contain a name that can be 
                            |used for this type name; information about 
                            |the root may need to be provided
                            |""".flattenIntoOneLine()
                    )
                }
                .map { lastPathSegment ->
                    RestApiSourceNamingConventions
                        .getResponseTypeNamingConventionForResponsePathName()
                        .deriveName(lastPathSegment)
                }
                .orElseThrow()
        return addNewOrUpdateExistingSwaggerSourceIndexToContext(
            DefaultSwaggerResponseTypeSourceContainerType(
                getDataSourceKeyForSwaggerSourceIndicesInContext(contextContainer),
                sourcePath,
                conventionalName,
                responseJsonSchema.toOption()
            ),
            contextContainer
        )
    }

    override fun createSourceAttributeInContextForPathInfoRepresentation(
        sourcePath: SchematicPath,
        pathInfo: PathItem,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT> {
        logger.debug(
            """create_source_attribute_in_context_for_path_info_representation: [ 
            |source_path: ${sourcePath}, 
            |path_info.post.response.size: ${pathInfo?.post?.responses?.size} 
            |]""".flattenIntoOneLine()
        )
        val lastPathSegment: String =
            sourcePath.pathSegments
                .lastOrNone()
                .successIfDefined {
                    RestApiDataSourceException(
                        RestApiErrorResponse.REST_API_DATA_SOURCE_CREATION_ERROR,
                        """path_item.source_path.path_segments 
                       |does not contain a name that can be 
                       |used for this type name: [ source_path: ${sourcePath}
                       |]""".flattenIntoOneLine()
                    )
                }
                .orElseThrow()
        val pathInfoAttributeName: ConventionalName =
            RestApiSourceNamingConventions.getFieldNamingConventionForPathName()
                .deriveName(lastPathSegment)
        return addNewOrUpdateExistingSwaggerSourceIndexToContext(
            DefaultSwaggerSourcePathItemAttribute(
                sourcePath,
                getDataSourceKeyForSwaggerSourceIndicesInContext(contextContainer),
                pathInfoAttributeName,
                pathInfo.some()
            ),
            contextContainer
        )
    }

    override fun createSourceAttributeInContextForPropertyOfSuccessfulApiResponseObject(
        sourcePath: SchematicPath,
        successfulApiResponse: ApiResponse,
        responseJsonSchema: Schema<*>,
        jsonPropertyName: String,
        jsonPropertySchema: Schema<*>,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT> {
        logger.debug(
            """create_source_attribute_in_context_for_property_of_successful_api_response_object: 
            |[ source_path: ${sourcePath}, 
            |json_property_name: ${jsonPropertyName}, 
            |json_property_schema.type: ${jsonPropertySchema.type} 
            |]""".flattenIntoOneLine()
        )
        val conventionalName: ConventionalName =
            RestApiSourceNamingConventions.getFieldNamingConventionForJsonPropertyName()
                .deriveName(jsonPropertyName)
        return addNewOrUpdateExistingSwaggerSourceIndexToContext(
            DefaultSwaggerSourceResponseBodyPropertyAttribute(
                getDataSourceKeyForSwaggerSourceIndicesInContext(contextContainer),
                conventionalName,
                sourcePath,
                jsonPropertyName.some(),
                jsonPropertySchema.some()
            ),
            contextContainer
        )
    }

    override fun createParameterContainerTypeForPostRequestBodyObject(
        sourcePath: SchematicPath,
        request: RequestBody,
        requestBodyJsonSchema: Schema<*>,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT> {
        val requestBodyPropertiesSize: Int =
            requestBodyJsonSchema.properties.toOption().mapNotNull { m -> m.size }.getOrElse { 0 }
        logger.debug(
            """create_parameter_container_type_for_post_request_body_object: 
                |[ source_path: ${sourcePath}, 
                |request_body_schema.type: ${requestBodyJsonSchema.type}, 
                |request_body_schema.size: $requestBodyPropertiesSize 
                |]""".flattenIntoOneLine()
        )
        val conventionalName: ConventionalName =
            sourcePath.pathSegments
                .lastOrNone()
                .successIfDefined {
                    RestApiDataSourceException(
                        RestApiErrorResponse.REST_API_DATA_SOURCE_CREATION_ERROR,
                        """request_type.source_path.path_segments 
                            |does not contain a name that can be 
                            |used for this type name; information about 
                            |the root may need to be provided
                            |""".flattenIntoOneLine()
                    )
                }
                .map { lastPathSegment ->
                    RestApiSourceNamingConventions
                        .getRequestTypeNamingConventionForRequestPathName()
                        .deriveName(lastPathSegment)
                }
                .orElseThrow()

        return addNewOrUpdateExistingSwaggerSourceIndexToContext(
            DefaultSwaggerParameterContainerType(
                getDataSourceKeyForSwaggerSourceIndicesInContext(contextContainer),
                sourcePath,
                conventionalName,
                requestBodyJsonSchema
            ),
            contextContainer
        )
    }

    override fun createParameterAttributeForPostRequestBodyObjectProperty(
        sourcePath: SchematicPath,
        request: RequestBody,
        requestBodyParentSchema: Schema<*>,
        jsonPropertyName: String,
        jsonPropertySchema: Schema<*>,
        contextContainer: SwaggerSourceIndexContextContainer<WT>,
    ): SwaggerSourceIndexContextContainer<WT> {
        logger.debug(
            """create_parameter_attribute_for_post_request_body_object_property: 
            |[ source_path: ${sourcePath}, 
            |json_property_name: ${jsonPropertyName}, 
            |json_property_schema.type: ${jsonPropertySchema.type} 
            |]""".flattenIntoOneLine()
        )
        val conventionalName: ConventionalName =
            RestApiSourceNamingConventions.getFieldNamingConventionForJsonPropertyName()
                .deriveName(jsonPropertyName)
        return addNewOrUpdateExistingSwaggerSourceIndexToContext(
            DefaultSwaggerParameterAttribute(
                getDataSourceKeyForSwaggerSourceIndicesInContext(contextContainer),
                sourcePath,
                conventionalName,
                jsonPropertyName,
                jsonPropertySchema
            ),
            contextContainer
        )
    }
}
