package funcify.feature.datasource.graphql.retrieval

import arrow.core.Either
import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import funcify.feature.datasource.graphql.GraphQLApiDataSource
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.datasource.graphql.schema.GraphQLParameterAttribute
import funcify.feature.datasource.graphql.schema.GraphQLSourceAttribute
import funcify.feature.datasource.graphql.schema.GraphQLSourceIndex
import funcify.feature.datasource.json.JsonNodeSchematicPathToValueMappingExtractor
import funcify.feature.datasource.retrieval.DataSourceRepresentativeJsonRetrievalStrategy
import funcify.feature.json.JsonMapper
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.index.CompositeSourceAttribute
import funcify.feature.schema.path.SchematicPath
import funcify.feature.schema.vertex.ParameterJunctionVertex
import funcify.feature.schema.vertex.ParameterLeafVertex
import funcify.feature.schema.vertex.SourceJunctionVertex
import funcify.feature.schema.vertex.SourceLeafVertex
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.PersistentMapExtensions.toPersistentMap
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.language.AstPrinter
import graphql.language.OperationDefinition
import java.util.concurrent.Executor
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import org.slf4j.Logger
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty

/**
 *
 * @author smccarron
 * @created 2022-08-12
 */
internal class DefaultGraphQLDataSourceJsonRetrievalStrategy(
    private val asyncExecutor: Executor,
    private val jsonMapper: JsonMapper,
    private val graphQLDataSource: GraphQLApiDataSource,
    override val parameterVertices:
        ImmutableSet<Either<ParameterJunctionVertex, ParameterLeafVertex>>,
    override val sourceVertices: ImmutableSet<Either<SourceJunctionVertex, SourceLeafVertex>>
) : DataSourceRepresentativeJsonRetrievalStrategy<GraphQLSourceIndex> {

    companion object {
        private val logger: Logger = loggerFor<DefaultGraphQLDataSourceJsonRetrievalStrategy>()
        private const val DATA_KEY = "data"
        private const val ERRORS_KEY = "errors"
    }

    override val dataSource: DataSource<GraphQLSourceIndex> = graphQLDataSource

    override val parameterPaths: ImmutableSet<SchematicPath> by lazy { super.parameterPaths }

    override val sourcePaths: ImmutableSet<SchematicPath> by lazy { super.sourcePaths }

    private val sourceVertexPathByGraphQLDataSourceIndexPath:
        ImmutableMap<SchematicPath, SchematicPath> by lazy {
        sourceVertices
            .asSequence()
            .filter { targetSjvOrSlv ->
                /*
                 * Any source_junction_vertex in the set must have at least one child source_leaf_vertex
                 * in the set or else, an exception similar to "Validation error of type SubSelectionRequired:"
                 * will be received on making the call to the graphql_api_data_source
                 */
                targetSjvOrSlv
                    .mapLeft { sjv ->
                        sourceVertices
                            .asSequence()
                            .filter { otherSjvOrSlv -> otherSjvOrSlv != targetSjvOrSlv }
                            .any { otherSjvOrSlv ->
                                otherSjvOrSlv
                                    .fold(SourceJunctionVertex::path, SourceLeafVertex::path)
                                    .isChildTo(sjv.path)
                            }
                    }
                    .fold({ sjvWithChild -> sjvWithChild }, { _ -> true })
            }
            .map { sjvOrSlv ->
                sjvOrSlv
                    .fold({ sjv -> sjv.compositeAttribute }, { slv -> slv.compositeAttribute })
                    .getSourceAttributeByDataSource()[graphQLDataSource.key]
                    .toOption()
                    .filterIsInstance<GraphQLSourceAttribute>()
                    .map { gqlSa ->
                        gqlSa.sourcePath to
                            sjvOrSlv.fold(SourceJunctionVertex::path, SourceLeafVertex::path)
                    }
            }
            .flatMapOptions()
            .reducePairsToPersistentMap()
    }

    private val graphQLDataSourceIndexPathByParameterVertexPath:
        ImmutableMap<SchematicPath, SchematicPath> by lazy {
        parameterVertices
            .asSequence()
            .map { pjvOrPlv ->
                pjvOrPlv
                    .fold(
                        { pjv -> pjv.compositeParameterAttribute },
                        { plv -> plv.compositeParameterAttribute }
                    )
                    .getParameterAttributesByDataSource()[graphQLDataSource.key]
                    .toOption()
                    .filterIsInstance<GraphQLParameterAttribute>()
                    .map { gqlPa ->
                        pjvOrPlv.fold(ParameterJunctionVertex::path, ParameterLeafVertex::path) to
                            gqlPa.sourcePath
                    }
            }
            .flatMapOptions()
            .reducePairsToPersistentMap()
    }

    private val queryOperationDefinitionFunction by lazy {
        GraphQLQueryPathBasedComposer
            .createQueryOperationDefinitionComposerForParameterAttributePathsAndValuesForTheseSourceAttributes(
                // Use the path on the data_source index since it may differ from the vertex path
                sourceVertexPathByGraphQLDataSourceIndexPath.keys.toPersistentSet()
            )
    }

    private val parentPathToVertex:
        Option<Pair<SchematicPath, Either<SourceJunctionVertex, SourceLeafVertex>>> by lazy {
        sourceVertices
            .asSequence()
            .minByOrNull { sjvOrSlv ->
                sjvOrSlv.fold(SourceJunctionVertex::path, SourceLeafVertex::path)
            }
            .toOption()
            .map { sjvOrSlv ->
                sjvOrSlv.fold(SourceJunctionVertex::path, SourceLeafVertex::path) to sjvOrSlv
            }
    }

    private val parentSourcePath: Option<SchematicPath> by lazy {
        parentPathToVertex
            .map { (_, sjvOrSlv) ->
                sjvOrSlv.fold(
                    SourceJunctionVertex::compositeAttribute,
                    SourceLeafVertex::compositeAttribute
                )
            }
            .flatMap { csa: CompositeSourceAttribute ->
                csa.getSourceAttributeByDataSource()[dataSource.key].toOption()
            }
            .filterIsInstance<GraphQLSourceAttribute>()
            .map { gsa: GraphQLSourceAttribute -> gsa.sourcePath }
    }

    init {
        parameterVertices
            .asSequence()
            .filterNot { pjvOrPlv ->
                pjvOrPlv.fold(
                    { pjv ->
                        pjv.compositeParameterAttribute
                            .getParameterAttributesByDataSource()
                            .keys
                            .contains(graphQLDataSource.key)
                    },
                    { plv ->
                        plv.compositeParameterAttribute
                            .getParameterAttributesByDataSource()
                            .keys
                            .contains(graphQLDataSource.key)
                    }
                )
            }
            .fold(persistentSetOf<Either<ParameterJunctionVertex, ParameterLeafVertex>>()) {
                ps,
                pjvOrPlv ->
                ps.add(pjvOrPlv)
            }
            .let { verticesWithoutMappingsForThisDataSource ->
                if (verticesWithoutMappingsForThisDataSource.isNotEmpty()) {
                    val vertexPaths =
                        verticesWithoutMappingsForThisDataSource
                            .asSequence()
                            .map { pjvOrPlv ->
                                pjvOrPlv.fold(
                                    ParameterJunctionVertex::path,
                                    ParameterLeafVertex::path
                                )
                            }
                            .joinToString(", ", "{ ", " }")
                    throw GQLDataSourceException(
                        GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                        """the following parameter_vertices do not have 
                            mappings to this data_source [ data_source.key: ${graphQLDataSource.key} ] 
                            [ vertices: $vertexPaths 
                            ]""".flatten()
                    )
                }
            }
        sourceVertices
            .asSequence()
            .filterNot { sjvOrSlv ->
                sjvOrSlv.fold(
                    { sjv ->
                        sjv.compositeAttribute
                            .getSourceAttributeByDataSource()
                            .keys
                            .contains(graphQLDataSource.key)
                    },
                    { slv ->
                        slv.compositeAttribute
                            .getSourceAttributeByDataSource()
                            .keys
                            .contains(graphQLDataSource.key)
                    }
                )
            }
            .fold(persistentSetOf<Either<SourceJunctionVertex, SourceLeafVertex>>()) { ps, sjvOrSlv
                ->
                ps.add(sjvOrSlv)
            }
            .let { verticesWithoutMappingsForThisDataSource ->
                if (verticesWithoutMappingsForThisDataSource.isNotEmpty()) {
                    val vertexPaths =
                        verticesWithoutMappingsForThisDataSource
                            .asSequence()
                            .map { sjvOrSlv ->
                                sjvOrSlv.fold(SourceJunctionVertex::path, SourceLeafVertex::path)
                            }
                            .joinToString(", ", "{ ", " }")
                    throw GQLDataSourceException(
                        GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                        """the following source_vertices do not have 
                            mappings to this data_source [ data_source.key: ${graphQLDataSource.key} ] 
                            [ vertices: $vertexPaths 
                            ]""".flatten()
                    )
                }
            }
    }

    override fun invoke(
        valuesByParameterPaths: ImmutableMap<SchematicPath, JsonNode>
    ): Mono<ImmutableMap<SchematicPath, JsonNode>> {
        val parameterPathsSetStr = valuesByParameterPaths.keys.joinToString(",", "{ ", " }")
        logger.debug("invoke: [ values_by_parameter_paths.keys: $parameterPathsSetStr ]")
        return attemptToCreateGraphQLQueryStringFromValuesByParameterPathsInput(
                valuesByParameterPaths
                    .asSequence()
                    .map { (vertexPath, jsonValue) ->
                        graphQLDataSourceIndexPathByParameterVertexPath[vertexPath]
                            .toOption()
                            .map { graphQLDataSourceParameterPath ->
                                graphQLDataSourceParameterPath to jsonValue
                            }
                    }
                    .flatMapOptions()
                    .reducePairsToPersistentMap(),
                parameterPathsSetStr
            )
            .toMono()
            .flatMap { queryString ->
                graphQLDataSource.graphQLApiService.executeSingleQuery(queryString)
            }
            .flatMap(convertResponseJsonIntoJsonValuesBySchematicPathMap())
            .doOnError(logErrorIfOccurred())
    }

    private fun attemptToCreateGraphQLQueryStringFromValuesByParameterPathsInput(
        valuesByParameterPaths: ImmutableMap<SchematicPath, JsonNode>,
        parameterPathsSetStr: String,
    ): Try<String> {
        return Try.attempt {
                queryOperationDefinitionFunction.invoke(
                    valuesByParameterPaths
                        .asSequence()
                        .filter { (parameterPath, _) -> parameterPath in parameterPaths }
                        .toPersistentMap()
                )
            }
            .map { queryOperationDefinition: OperationDefinition ->
                AstPrinter.printAst(queryOperationDefinition)
            }
            .mapFailure { t ->
                GQLDataSourceException(
                    GQLDataSourceErrorResponse.UNEXPECTED_ERROR,
                    """error occurred when transforming source and 
                       |parameter paths and values into graphql 
                       |query string
                       |[ parameter_paths: $parameterPathsSetStr ]""".flatten(),
                    t
                )
            }
    }

    private fun convertResponseJsonIntoJsonValuesBySchematicPathMap():
        (JsonNode) -> Mono<out ImmutableMap<SchematicPath, JsonNode>> {
        return { responseJson: JsonNode ->
            when {
                responseJson.has(ERRORS_KEY) &&
                    responseJson.path(ERRORS_KEY).isArray &&
                    !responseJson.path(ERRORS_KEY).isEmpty -> {
                    Mono.error {
                        val errorContent: String =
                            responseJson
                                .toOption()
                                .map { jn -> jn.path(ERRORS_KEY) }
                                .filterIsInstance<ArrayNode>()
                                .fold(::emptySequence, ArrayNode::asSequence)
                                .withIndex()
                                .joinToString("; ", "errors reported: { ", " }") { idxNode ->
                                    String.format("[%d]: %s", idxNode.index, idxNode.value)
                                }
                        GQLDataSourceException(
                            GQLDataSourceErrorResponse.CLIENT_ERROR,
                            errorContent
                        )
                    }
                }
                else ->
                    Mono.just(responseJson)
                        .filter { jn ->
                            jn.has(DATA_KEY) &&
                                jn.path(DATA_KEY).isObject &&
                                !jn.path(DATA_KEY).isEmpty
                        }
                        .switchIfEmpty {
                            Mono.error<JsonNode> {
                                GQLDataSourceException(
                                    GQLDataSourceErrorResponse.MALFORMED_CONTENT_RECEIVED,
                                    """response_json from 
                                    |[ graphql_data_source.service.name: ${graphQLDataSource.graphQLApiService.serviceName} ] 
                                    |is not in the expected format; 
                                    |lacks an non-empty object value for 
                                    |key [ ${DATA_KEY} ]""".flatten()
                                )
                            }
                        }
                        .map { jn -> jn.path(DATA_KEY) }
                        .map(JsonNodeSchematicPathToValueMappingExtractor)
                        .doOnNext { resultMap ->
                            logger.info(
                                "graphql_data_source_json_retrieval_strategy: [ status: successful ] [ result: {} ]",
                                resultMap
                                    .asSequence()
                                    .joinToString(
                                        separator = ",\n",
                                        prefix = "{ ",
                                        postfix = " }",
                                        transform = { (k, v) -> "$k: $v" }
                                    )
                            )
                        }
            }
        }
    }

    private fun logErrorIfOccurred(): (Throwable) -> Unit {
        return { t: Throwable ->
            logger.error(
                "graphql_data_source_json_retrieval_strategy: [ status: failed ] [ type: {}, message: {} ]",
                t::class.simpleName,
                t.message
            )
        }
    }
}
