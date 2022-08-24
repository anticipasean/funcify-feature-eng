package funcify.feature.datasource.rest.retrieval

import arrow.core.Either
import arrow.core.filterIsInstance
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatTypes
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.JsonNodeType
import funcify.feature.datasource.json.JsonNodeSchematicPathToValueMappingExtractor
import funcify.feature.datasource.rest.RestApiDataSource
import funcify.feature.datasource.rest.error.RestApiDataSourceException
import funcify.feature.datasource.rest.error.RestApiErrorResponse
import funcify.feature.datasource.rest.schema.RestApiSourceIndex
import funcify.feature.datasource.rest.schema.SwaggerParameterAttribute
import funcify.feature.datasource.rest.schema.SwaggerRestApiSourceMetamodel
import funcify.feature.datasource.rest.schema.SwaggerSourceAttribute
import funcify.feature.datasource.retrieval.DataSourceRepresentativeJsonRetrievalStrategy
import funcify.feature.json.JsonMapper
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.attempt.Try.Companion.filterInstanceOf
import funcify.feature.tools.container.deferred.Deferred
import funcify.feature.tools.extensions.DeferredExtensions.toDeferred
import funcify.feature.tools.extensions.FunctionExtensions.compose
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import io.swagger.v3.oas.models.media.Schema
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import org.slf4j.Logger
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientResponse
import reactor.core.publisher.Mono

/**
 *
 * @author smccarron
 * @created 2022-08-15
 */
internal class DefaultSwaggerRestDataSourceJsonRetrievalStrategy(
    private val jsonMapper: JsonMapper,
    override val dataSource: RestApiDataSource,
    override val parameterVertices:
        ImmutableSet<Either<ParameterJunctionVertex, ParameterLeafVertex>>,
    override val sourceVertices: ImmutableSet<Either<SourceJunctionVertex, SourceLeafVertex>>
) : DataSourceRepresentativeJsonRetrievalStrategy<RestApiSourceIndex> {

    companion object {
        private val logger: Logger = loggerFor<DefaultSwaggerRestDataSourceJsonRetrievalStrategy>()
    }

    private val parentPathVertexPair:
        Pair<SchematicPath, Either<SourceJunctionVertex, SourceLeafVertex>>

    private val parentVertexPathToSwaggerSourceAttribute:
        Pair<SchematicPath, SwaggerSourceAttribute>

    private val swaggerSourceAttributesByVertexPath:
        ImmutableMap<SchematicPath, SwaggerSourceAttribute>

    private val swaggerParameterAttributesByVertexPath:
        ImmutableMap<SchematicPath, SwaggerParameterAttribute>

    init {
        Try.attempt { dataSource.sourceMetamodel }
            .filterInstanceOf<SwaggerRestApiSourceMetamodel>()
            .mapFailure { t ->
                RestApiDataSourceException(
                    RestApiErrorResponse.UNEXPECTED_ERROR,
                    """data_source not a swagger_rest_api_data_source: 
                        |[ expected: source_metamodel.type ${SwaggerRestApiSourceMetamodel::class.qualifiedName}, 
                        |actual: source_metamodel.type 
                        |${dataSource.sourceMetamodel::class.qualifiedName} 
                        |]""".flatten(),
                    t
                )
            }
        parentPathVertexPair =
            sourceVertices
                .asSequence()
                .map { sjvOrSlv ->
                    sjvOrSlv.fold(SourceJunctionVertex::path, SourceLeafVertex::path) to sjvOrSlv
                }
                .minByOrNull { (p, _) -> p.level() }
                .toOption()
                .filter { (_, parentVertex) ->
                    when (
                        val swaggerSourceAttribute: SwaggerSourceAttribute? =
                            parentVertex
                                .fold(
                                    SourceJunctionVertex::compositeAttribute,
                                    SourceLeafVertex::compositeAttribute
                                )
                                .getSourceAttributeByDataSource()[dataSource.key]
                                .toOption()
                                .filterIsInstance<SwaggerSourceAttribute>()
                                .orNull()
                    ) {
                        null -> false
                        else -> {
                            swaggerSourceAttribute.representsPathItem()
                        }
                    }
                }
                .map { (parentPath, parentVertex) ->
                    Try.attemptSequence(
                            sourceVertices
                                .asSequence()
                                .map { sjvOrSlv ->
                                    sjvOrSlv.fold(
                                        SourceJunctionVertex::path,
                                        SourceLeafVertex::path
                                    )
                                }
                                .map { possibleChildPath ->
                                    Try.fromOption(
                                        possibleChildPath.toOption().filter { cp ->
                                            cp.isDescendentOf(parentPath)
                                        }
                                    ) { _: NoSuchElementException ->
                                        RestApiDataSourceException(
                                            RestApiErrorResponse.INVALID_INPUT,
                                            """vertex is not a child of assessed parent vertex: 
                                            |[ parent_path: ${parentPath}, vertex.path: ${possibleChildPath} 
                                            |]""".flatten()
                                        )
                                    }
                                }
                        )
                        .map { childPathsSeq -> parentPath to parentVertex }
                }
                .fold({
                    throw RestApiDataSourceException(
                        RestApiErrorResponse.INVALID_INPUT,
                        """one vertex of the given source_vertices must serve as the 
                        |'parent', mapping all other paths to response json_properties 
                        |on the object to which the 'parent' points""".flatten()
                    )
                }) { parentPathVertexPairAttempt -> parentPathVertexPairAttempt.orElseThrow() }

        // Already assessed in body of preceding validation so no need for custom exception for this
        // case
        parentVertexPathToSwaggerSourceAttribute =
            parentPathVertexPair.second
                .fold(
                    SourceJunctionVertex::compositeAttribute,
                    SourceLeafVertex::compositeAttribute
                )
                .getSourceAttributeByDataSource()[dataSource.key]
                .toOption()
                .filterIsInstance<SwaggerSourceAttribute>()
                .map { ssa -> parentPathVertexPair.first to ssa }
                .successIfDefined()
                .orElseThrow()

        swaggerSourceAttributesByVertexPath =
            Try.attemptSequence(
                    sourceVertices.asSequence().map { sjvOrSlv ->
                        Try.fromOption(
                            sjvOrSlv
                                .fold(
                                    SourceJunctionVertex::compositeAttribute,
                                    SourceLeafVertex::compositeAttribute
                                )
                                .getSourceAttributeByDataSource()[dataSource.key]
                                .toOption()
                                .filterIsInstance<SwaggerSourceAttribute>()
                                .map { ssa ->
                                    sjvOrSlv.fold(
                                        SourceJunctionVertex::path,
                                        SourceLeafVertex::path
                                    ) to ssa
                                }
                        ) { _: NoSuchElementException ->
                            val vertexDataSourceKeysAsString =
                                sjvOrSlv
                                    .fold(
                                        SourceJunctionVertex::compositeAttribute,
                                        SourceLeafVertex::compositeAttribute
                                    )
                                    .getSourceAttributeByDataSource()
                                    .keys
                                    .asSequence()
                                    .joinToString(", ", "{ ", " }")
                            RestApiDataSourceException(
                                RestApiErrorResponse.UNEXPECTED_ERROR,
                                """all source_vertices must have a representative source_index 
                                   |in the data_source provided for use in this 
                                   |${DefaultSwaggerRestDataSourceJsonRetrievalStrategy::class.qualifiedName} 
                                   |[ vertex.path: ${sjvOrSlv.fold(SourceJunctionVertex::path, SourceLeafVertex::path)} ] 
                                   |[ expected: one key for ${dataSource.key}, 
                                   |actual: ${vertexDataSourceKeysAsString} ]""".flatten()
                            )
                        }
                    }
                )
                .map { swaggerSrcAttrByVertexPathPairs ->
                    swaggerSrcAttrByVertexPathPairs.reducePairsToPersistentMap()
                }
                .orElseThrow()
        swaggerParameterAttributesByVertexPath =
            Try.attemptSequence(
                    parameterVertices.asSequence().map { pjvOrPlv ->
                        Try.fromOption(
                            pjvOrPlv
                                .fold(
                                    ParameterJunctionVertex::compositeParameterAttribute,
                                    ParameterLeafVertex::compositeParameterAttribute
                                )
                                .getParameterAttributesByDataSource()[dataSource.key]
                                .toOption()
                                .filterIsInstance<SwaggerParameterAttribute>()
                                .map { spa ->
                                    pjvOrPlv.fold(
                                        ParameterJunctionVertex::path,
                                        ParameterLeafVertex::path
                                    ) to spa
                                }
                        ) { _: NoSuchElementException ->
                            val vertexDataSourceKeysAsString =
                                pjvOrPlv
                                    .fold(
                                        ParameterJunctionVertex::compositeParameterAttribute,
                                        ParameterLeafVertex::compositeParameterAttribute
                                    )
                                    .getParameterAttributesByDataSource()
                                    .keys
                                    .asSequence()
                                    .joinToString(", ", "{ ", " }")
                            RestApiDataSourceException(
                                RestApiErrorResponse.UNEXPECTED_ERROR,
                                """all parameter_vertices must have a representative source_index 
                                   |in the data_source provided for use in this 
                                   |${DefaultSwaggerRestDataSourceJsonRetrievalStrategy::class.qualifiedName} 
                                   |[ vertex.path: ${pjvOrPlv.fold(ParameterJunctionVertex::path, ParameterLeafVertex::path)} ] 
                                   |[ expected: one key for ${dataSource.key}, 
                                   |actual: ${vertexDataSourceKeysAsString} ]""".flatten()
                            )
                        }
                    }
                )
                .map { swaggerParamAttrByVertexPathPairs ->
                    swaggerParamAttrByVertexPathPairs.reducePairsToPersistentMap()
                }
                .orElseThrow()
    }

    private val sourceVertexPathBySourceIndexPath:
        ImmutableMap<SchematicPath, SchematicPath> by lazy {
        swaggerSourceAttributesByVertexPath
            .asSequence()
            .map { (vertexPath, swaggerSrcAttr) -> swaggerSrcAttr.sourcePath to vertexPath }
            .reducePairsToPersistentMap()
    }

    override fun invoke(
        valuesByParameterPaths: ImmutableMap<SchematicPath, JsonNode>
    ): Deferred<ImmutableMap<SchematicPath, JsonNode>> {
        val parameterPathsAsString = valuesByParameterPaths.keys.joinToString(", ", "{ ", " }")
        logger.debug("invoke: [ values_by_parameter_paths.keys: $parameterPathsAsString ] ]")
        return attemptToCreateRequestJsonObjectFromValuesByParameterPaths(valuesByParameterPaths)
            .toDeferred()
            .flatMap(makeRequestToRestApiDataSourceWithJsonPayload())
            .flatMap(convertJsonResponseIntoValuesBySourceVertexPathsMap())
    }

    private fun attemptToCreateRequestJsonObjectFromValuesByParameterPaths(
        valuesByParameterPaths: ImmutableMap<SchematicPath, JsonNode>
    ): Try<JsonNode> {
        return Try.attemptSequence(
                valuesByParameterPaths.asSequence().map { (vertexPath, jsonValue) ->
                    Try.fromOption(swaggerParameterAttributesByVertexPath[vertexPath].toOption()) {
                            _: NoSuchElementException ->
                            RestApiDataSourceException(
                                RestApiErrorResponse.INVALID_INPUT,
                                """parameter_vertex.path not found within 
                                   |expected parameter_vertex paths for this 
                                   |${DefaultSwaggerRestDataSourceJsonRetrievalStrategy::class.qualifiedName}: 
                                   |[ actual: ${vertexPath} ]""".flatten()
                            )
                        }
                        .flatMap { parameterAttr ->
                            Try.fromOption(
                                    jsonValue.toOption().filter { jn ->
                                        jn.nodeType == JsonNodeType.NULL ||
                                            jsonSchemaToJsonTypeConverter()
                                                .invoke(parameterAttr.jsonSchema) == jn.nodeType
                                    }
                                ) { _: NoSuchElementException ->
                                    RestApiDataSourceException(
                                        RestApiErrorResponse.INVALID_INPUT,
                                        """value for parameter_attribute does not match 
                                           |NULL node type or schema-stated node type: 
                                           |[ expected: ${jsonSchemaToJsonTypeConverter().invoke(parameterAttr.jsonSchema)} 
                                           |or ${JsonFormatTypes.NULL.value()}, 
                                           |actual: ${jsonValue.nodeType}""".flatten()
                                    )
                                }
                                .map { jn -> parameterAttr to jn }
                        }
                }
            )
            .map { paramSourceIndexToValuePairs ->
                paramSourceIndexToValuePairs.fold(JsonNodeFactory.instance.objectNode()) {
                    requestJsonNode,
                    (paramAttr, jsonValue) ->
                    requestJsonNode.set(paramAttr.jsonPropertyName, jsonValue)
                }
            }
    }

    private fun jsonSchemaToJsonTypeConverter(): (Schema<*>) -> JsonNodeType {
        return jsonFormatTypeToJsonNodeTypeConverter().compose<
            Schema<*>, JsonFormatTypes?, JsonNodeType> { s: Schema<*> ->
            JsonFormatTypes.forValue(s.type)
        }
    }

    private fun jsonFormatTypeToJsonNodeTypeConverter(): (JsonFormatTypes?) -> JsonNodeType {
        return { jsonFormatTypes: JsonFormatTypes? ->
            when (jsonFormatTypes) {
                null -> JsonNodeType.NULL
                JsonFormatTypes.STRING -> JsonNodeType.STRING
                JsonFormatTypes.NUMBER -> JsonNodeType.NUMBER
                JsonFormatTypes.INTEGER -> JsonNodeType.NUMBER
                JsonFormatTypes.BOOLEAN -> JsonNodeType.BOOLEAN
                JsonFormatTypes.OBJECT -> JsonNodeType.OBJECT
                JsonFormatTypes.ARRAY -> JsonNodeType.ARRAY
                JsonFormatTypes.NULL -> JsonNodeType.NULL
                JsonFormatTypes.ANY -> JsonNodeType.POJO
            }
        }
    }

    private fun makeRequestToRestApiDataSourceWithJsonPayload(): (JsonNode) -> Deferred<JsonNode> {
        return { requestBodyJson: JsonNode ->
            parentVertexPathToSwaggerSourceAttribute.second.servicePathItemName
                .successIfDefined {
                    RestApiDataSourceException(
                        RestApiErrorResponse.REST_API_DATA_SOURCE_CREATION_ERROR,
                        """swagger_source_attributes acting as parent_vertices 
                            |must have a service_path_item_name defined: 
                            |[ swagger_source_attribute: ${parentVertexPathToSwaggerSourceAttribute.second} 
                            |]""".flatten()
                    )
                }
                .toDeferred()
                .flatMapMono { pathString ->
                    dataSource.restApiService
                        .getWebClient()
                        .post()
                        .uri { uriBuilder -> uriBuilder.path(pathString).build() }
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                            jsonMapper.fromJsonNode(requestBodyJson).toJsonString().toDeferred(),
                            String::class.java
                        )
                        .exchangeToMono { clientResponse: ClientResponse ->
                            // TODO: The handling here will definitely need to be more nuanced once
                            // error_responses by status_code are added to source_indexing for
                            // swagger_sources
                            if (clientResponse.statusCode().is2xxSuccessful) {
                                clientResponse.bodyToMono(JsonNode::class.java)
                            } else {
                                clientResponse.bodyToMono(String::class.java).flatMap { errorMessage
                                    ->
                                    Mono.error(
                                        RestApiDataSourceException(
                                            RestApiErrorResponse.CLIENT_ERROR,
                                            "client_response from service not successful: [ error_message: ${errorMessage} ]"
                                        )
                                    )
                                }
                            }
                        }
                }
        }
    }

    private fun convertJsonResponseIntoValuesBySourceVertexPathsMap():
        (JsonNode) -> Deferred<ImmutableMap<SchematicPath, JsonNode>> {
        return { responseJsonNode: JsonNode ->
            Deferred.completed(
                JsonNodeSchematicPathToValueMappingExtractor.invoke(responseJsonNode)
                    .asSequence()
                    .map { (sourcePath, jsonValue) ->
                        SchematicPath.of {
                            pathSegments(
                                parentVertexPathToSwaggerSourceAttribute.second.sourcePath
                                    .pathSegments
                                    .asSequence()
                                    .plus(sourcePath.pathSegments)
                                    .toList()
                            )
                        } to jsonValue
                    }
                    .map { (remappedSourcePath, jsonValue) ->
                        sourceVertexPathBySourceIndexPath[remappedSourcePath].toOption().map {
                            sourceVertexPath ->
                            sourceVertexPath to jsonValue
                        }
                    }
                    .flatMapOptions()
                    .reducePairsToPersistentMap()
            )
        }
    }
}
