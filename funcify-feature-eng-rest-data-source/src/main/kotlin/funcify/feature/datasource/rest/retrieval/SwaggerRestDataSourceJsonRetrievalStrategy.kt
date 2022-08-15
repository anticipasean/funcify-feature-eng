package funcify.feature.datasource.rest.retrieval

import arrow.core.Either
import arrow.core.filterIsInstance
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatTypes
import com.fasterxml.jackson.databind.node.JsonNodeType
import funcify.feature.datasource.rest.RestApiDataSource
import funcify.feature.datasource.rest.error.RestApiDataSourceException
import funcify.feature.datasource.rest.error.RestApiErrorResponse
import funcify.feature.datasource.rest.schema.RestApiSourceIndex
import funcify.feature.datasource.rest.schema.SwaggerParameterAttribute
import funcify.feature.datasource.rest.schema.SwaggerRestApiSourceMetamodel
import funcify.feature.datasource.rest.schema.SwaggerSourceAttribute
import funcify.feature.datasource.retrieval.DataSourceSpecificJsonRetrievalStrategy
import funcify.feature.json.JsonMapper
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.attempt.Try.Companion.filterInstanceOf
import funcify.feature.tools.container.deferred.Deferred
import funcify.feature.tools.extensions.FunctionExtensions.compose
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import io.swagger.v3.oas.models.media.Schema
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-08-15
 */
internal class SwaggerRestDataSourceJsonRetrievalStrategy(
    internal val jsonMapper: JsonMapper,
    override val dataSource: RestApiDataSource,
    override val parameterVertices:
        ImmutableSet<Either<ParameterJunctionVertex, ParameterLeafVertex>>,
    override val sourceVertices: ImmutableSet<Either<SourceJunctionVertex, SourceLeafVertex>>
) : DataSourceSpecificJsonRetrievalStrategy<RestApiSourceIndex> {

    companion object {
        private val logger: Logger = loggerFor<SwaggerRestDataSourceJsonRetrievalStrategy>()
    }

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
        sourceVertices
            .asSequence()
            .map { sjvOrSlv ->
                sjvOrSlv.fold(SourceJunctionVertex::path, SourceLeafVertex::path) to sjvOrSlv
            }
            .minByOrNull { (p, _) -> p.level() }
            .toOption().successIfDefined {
                RestApiDataSourceException(RestApiErrorResponse.INVALID_INPUT, """one vertex of the given source_vertices must serve as the 'parent' mapping to the path """)
            }

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
                                   |${SwaggerRestDataSourceJsonRetrievalStrategy::class.qualifiedName} 
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
                                   |${SwaggerRestDataSourceJsonRetrievalStrategy::class.qualifiedName} 
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

    override fun invoke(
        valuesByParameterPaths: ImmutableMap<SchematicPath, JsonNode>
    ): Deferred<ImmutableMap<SchematicPath, JsonNode>> {
        val parameterPathsAsString = valuesByParameterPaths.keys.joinToString(", ", "{ ", " }")
        logger.debug("invoke: [ values_by_parameter_paths.keys: $parameterPathsAsString ] ]")
        attemptToCreateRequestJsonObjectFromValuesByParameterPaths(valuesByParameterPaths)
        TODO()
    }

    private fun attemptToCreateRequestJsonObjectFromValuesByParameterPaths(
        valuesByParameterPaths: ImmutableMap<SchematicPath, JsonNode>
    ): Try<JsonNode> {
        Try.attemptSequence(
            valuesByParameterPaths.asSequence().map { (vertexPath, jsonValue) ->
                Try.fromOption(swaggerParameterAttributesByVertexPath[vertexPath].toOption()) {
                        _: NoSuchElementException ->
                        RestApiDataSourceException(
                            RestApiErrorResponse.INVALID_INPUT,
                            """parameter_vertex.path not found within 
                            |expected parameter_vertex paths for this 
                            |${SwaggerRestDataSourceJsonRetrievalStrategy::class.qualifiedName}: 
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
                    }
            }
        )
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
}
