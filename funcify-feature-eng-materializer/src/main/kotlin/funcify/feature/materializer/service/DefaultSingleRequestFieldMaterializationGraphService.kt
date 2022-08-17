package funcify.feature.materializer.service

import arrow.core.Either
import arrow.core.Option
import arrow.core.left
import arrow.core.none
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.google.common.cache.CacheBuilder
import funcify.feature.datasource.retrieval.SchematicPathBasedJsonRetrievalFunctionFactory
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession
import funcify.feature.materializer.json.GraphQLValueToJsonNodeConverter
import funcify.feature.materializer.schema.RequestParameterEdge
import funcify.feature.materializer.schema.RequestParameterEdgeFactory
import funcify.feature.schema.SchematicEdge
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.deferred.Deferred
import funcify.feature.tools.container.graph.PathBasedGraph
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.SequenceExtensions.recurse
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.execution.ResultPath
import graphql.language.Argument
import graphql.language.Field
import java.util.concurrent.ConcurrentMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-08-08
 */
internal class DefaultSingleRequestFieldMaterializationGraphService(
    private val schematicPathBasedJsonRetrievalFunctionFactory:
        SchematicPathBasedJsonRetrievalFunctionFactory,
    private val requestParameterEdgeFactory: RequestParameterEdgeFactory,
) : SingleRequestFieldMaterializationGraphService {

    companion object {
        private val logger: Logger =
            loggerFor<DefaultSingleRequestFieldMaterializationGraphService>()
    }

    /**
     * These do not need to be recalculated on each request and remain consistent even when schema
     * has been updated
     */
    private val schematicPathForResultPathCache: ConcurrentMap<ResultPath, SchematicPath> by lazy {
        CacheBuilder.newBuilder().build<ResultPath, SchematicPath>().asMap()
    }

    override fun createMaterializationGraphForSession(
        session: SingleRequestFieldMaterializationSession
    ): Deferred<SingleRequestFieldMaterializationSession> {
        logger.debug(
            "create_materialization_graph_for_session: [ session.session_id: ${session.sessionId} ]"
        )
        logger.debug(
            "field.selection_set: [ session.field.selection_set: ${session.field.selectionSet} ]"
        )
        logger.debug(
            "execution_step_info: [ {} ]",
            session.dataFetchingEnvironment.executionStepInfo.simplePrint()
        )
        val sourceVertexPath: SchematicPath = getSourceVertexPathForSelectedFieldInSession(session)
        val parameterVertexPaths: ImmutableSet<SchematicPath> =
            calculateParameterVertexSchematicPathsForSourceVertexPathAndSession(
                sourceVertexPath,
                session
            )
        matchSourceVertexPathAndParameterVertexPathsToVerticesInMetamodelPathBasedGraph(
                session,
                sourceVertexPath,
                parameterVertexPaths
            )
            .peekIfSuccess { (sourceVertex, parameterVertices) ->
                logger.debug(
                    """selected_vertices: [ source_vertex: ${sourceVertex.path}, 
                    |parameter_vertices: ${parameterVertices.joinToString(", ")} 
                    |]""".flatten()
                )
            }
            .map { (sourceJunctionVertex, parameterVerticesSet) -> TODO() }

        return Deferred.completed(session)
    }

    private fun matchSourceVertexPathAndParameterVertexPathsToVerticesInMetamodelPathBasedGraph(
        session: SingleRequestFieldMaterializationSession,
        sourceVertexPath: SchematicPath,
        parameterVertexPaths: ImmutableSet<SchematicPath>,
    ): Try<Pair<SchematicVertex, ImmutableSet<SchematicVertex>>> {
        return session.metamodelGraph.pathBasedGraph
            .getVertex(sourceVertexPath)
            .successIfDefined {
                MaterializerException(
                    MaterializerErrorResponse.SCHEMA_INTEGRITY_ERROR,
                    """source_junction_vertex expected in 
                       |metamodel_graph.path_based_graph for path 
                       |[ schematic_path: $sourceVertexPath ]""".flatten()
                )
            }
            .zip(
                Try.attemptSequence(
                    parameterVertexPaths.asSequence().map { pvp ->
                        session.metamodelGraph.pathBasedGraph.getVertex(pvp).successIfDefined {
                            MaterializerException(
                                MaterializerErrorResponse.SCHEMA_INTEGRITY_ERROR,
                                """parameter_vertex expected in 
                                   |metamodel_graph.path_based_graph for path 
                                   |[ schematic_path: $pvp ]""".flatten()
                            )
                        }
                    }
                )
            ) { sourceJunctionVertex, parameterVerticesSequence ->
                sourceJunctionVertex to parameterVerticesSequence.toImmutableSet()
            }
    }

    private fun getSourceVertexPathForSelectedFieldInSession(
        session: SingleRequestFieldMaterializationSession
    ): SchematicPath {
        return schematicPathForResultPathCache.computeIfAbsent(
            session.dataFetchingEnvironment.executionStepInfo.path,
            calculateSourceVertexSchematicPathForGraphQLResultPath()
        )
    }

    private fun calculateSourceVertexSchematicPathForGraphQLResultPath():
        (ResultPath) -> SchematicPath {
        return { resultPath: ResultPath ->
            when {
                resultPath.isRootPath -> {
                    SchematicPath.getRootPath()
                }
                else -> {
                    SchematicPath.of { pathSegments(resultPath.keysOnly) }
                }
            }
        }
    }

    private fun calculateParameterVertexSchematicPathsForSourceVertexPathAndSession(
        sourceVertexPath: SchematicPath,
        session: SingleRequestFieldMaterializationSession
    ): ImmutableSet<SchematicPath> {
        return session.dataFetchingEnvironment.executionStepInfo.arguments.entries
            .asSequence()
            .map { (argName, _) -> sourceVertexPath.transform { argument(argName) } }
            .toImmutableSet()
    }

    private fun createEdgesInGraphMappingParameterVerticesToSourceVertex(
        session: SingleRequestFieldMaterializationSession,
    ): PathBasedGraph<SchematicPath, SchematicVertex, SchematicEdge> {
        sequenceOf(GraphConnectionsContext(currentFieldOrArgument = session.field.left()))
            .recurse { ctx ->
                makeConnectionsFromParentToChildFieldOrArgumentInContext(session, ctx)
            }
            .fold(
                PathBasedGraph.emptyTwoToOnePathsToEdgeGraph<
                    SchematicPath, SchematicVertex, RequestParameterEdge>()
            ) { g, connection ->
                when {
                    !g.getVertex(connection.vertex1.path).isDefined() &&
                        !g.getVertex(connection.vertex2.path).isDefined() -> {
                        g.putVertex(connection.vertex1.path, connection.vertex1)
                            .putVertex(connection.vertex2.path, connection.vertex2)
                            .putEdge(connection.edge.id, connection.edge)
                    }
                    !g.getVertex(connection.vertex2.path).isDefined() -> {
                        g.putVertex(connection.vertex2.path, connection.vertex2)
                            .putEdge(connection.edge.id, connection.edge)
                    }
                    else -> {
                        g.putEdge(connection.edge.id, connection.edge)
                    }
                }
            }

        TODO()
    }

    private data class GraphConnectionsContext(
        val parentPath: SchematicPath = SchematicPath.getRootPath(),
        val parentVertex: Option<SchematicVertex> = none(),
        val currentFieldOrArgument: Either<Field, Argument>
    )

    private data class GraphConnection(
        val vertex1: SchematicVertex,
        val vertex2: SchematicVertex,
        val edge: RequestParameterEdge,
    )

    private fun makeConnectionsFromParentToChildFieldOrArgumentInContext(
        session: SingleRequestFieldMaterializationSession,
        context: GraphConnectionsContext
    ): Sequence<Either<GraphConnectionsContext, GraphConnection>> {
        return when (
            val fieldOrArgument: Either<Field, Argument> = context.currentFieldOrArgument
        ) {
            is Either.Left -> {
                val field: Field = fieldOrArgument.value
                val vertexPath: SchematicPath =
                    SchematicPath.of {
                        pathSegments(
                            context.parentPath.pathSegments.asSequence().plus(field.name).toList()
                        )
                    }
                val sourceVertex =
                    session.metamodelGraph.pathBasedGraph
                        .getVertex(vertexPath)
                        .successIfDefined(sourceVertexNotFoundExceptionSupplier(vertexPath))
                        .orElseThrow()
                if (context.parentVertex.isDefined()) {
                        val edge: RequestParameterEdge =
                            requestParameterEdgeFactory
                                .builder()
                                .fromPathToPath(context.parentPath, vertexPath)
                                .extractionFromAncestorFunction { parentMap ->
                                    parentMap[vertexPath].toOption()
                                }
                                .build()
                        sequenceOf(
                            GraphConnection(context.parentVertex.orNull()!!, sourceVertex, edge)
                                .right()
                        )
                    } else {
                        emptySequence()
                    }
                    .plus(
                        field.arguments.asSequence().map { argument: Argument ->
                            GraphConnectionsContext(
                                    vertexPath,
                                    sourceVertex.some(),
                                    argument.right()
                                )
                                .left()
                        }
                    )
            }
            is Either.Right -> {
                // TODO: Break argument values down, if non-scalar, such that each constituent
                // parameter leaf vertex gets its own edges
                val argument: Argument = fieldOrArgument.value
                val vertexPath: SchematicPath =
                    SchematicPath.of {
                        pathSegments(context.parentPath.pathSegments).argument(argument.name)
                    }
                val parentVertex: SchematicVertex =
                    context.parentVertex
                        .successIfDefined(possibleOutOfOrderProcessingExceptionSupplier(vertexPath))
                        .orElseThrow()
                val parameterVertex =
                    session.metamodelGraph.pathBasedGraph
                        .getVertex(vertexPath)
                        .successIfDefined(parameterVertexNotFoundExceptionSupplier(vertexPath))
                        .orElseThrow()
                val argumentJsonValue: JsonNode =
                    GraphQLValueToJsonNodeConverter.invoke(argument.value)
                        .successIfDefined(
                            argumentValueNotResolvedIntoJsonExceptionSupplier(vertexPath, argument)
                        )
                        .orElseThrow()
                val edge =
                    if (argumentJsonValue.isNull) {
                        requestParameterEdgeFactory
                            .builder()
                            .fromPathToPath(context.parentPath, vertexPath)
                            .missingContextValue()
                            .build()
                    } else {
                        requestParameterEdgeFactory
                            .builder()
                            .fromPathToPath(context.parentPath, vertexPath)
                            .materializedValue(argumentJsonValue)
                            .build()
                    }
                sequenceOf(GraphConnection(parentVertex, parameterVertex, edge).right())
            }
        }
    }

    private fun argumentValueNotResolvedIntoJsonExceptionSupplier(
        vertexPath: SchematicPath,
        argument: Argument
    ): () -> MaterializerException {
        return { ->
            MaterializerException(
                MaterializerErrorResponse.UNEXPECTED_ERROR,
                """graphql_value [ type: ${argument.value::class.qualifiedName} ] 
                   |for argument [ name: ${argument.name} ] 
                   |could not be resolved in JSON for 
                   |[ vertex_path: ${vertexPath} ]""".flatten()
            )
        }
    }

    private fun sourceVertexNotFoundExceptionSupplier(
        vertexPath: SchematicPath
    ): () -> MaterializerException {
        return { ->
            MaterializerException(
                MaterializerErrorResponse.SCHEMA_INTEGRITY_ERROR,
                """source_junction_vertex or source_leaf_vertex 
                   |expected but not found in 
                   |metamodel_graph.path_based_graph for path 
                   |[ vertex_path: $vertexPath ]""".flatten()
            )
        }
    }

    private fun parameterVertexNotFoundExceptionSupplier(
        vertexPath: SchematicPath
    ): () -> MaterializerException {
        return { ->
            MaterializerException(
                MaterializerErrorResponse.SCHEMA_INTEGRITY_ERROR,
                """parameter_junction_vertex or parameter_leaf_vertex 
                   |expected in 
                   |metamodel_graph.path_based_graph for path 
                   |but not found 
                   |[ vertex_path: $vertexPath ]""".flatten()
            )
        }
    }

    private fun possibleOutOfOrderProcessingExceptionSupplier(
        vertexPath: SchematicPath
    ): () -> MaterializerException {
        return { ->
            MaterializerException(
                MaterializerErrorResponse.UNEXPECTED_ERROR,
                """vertices are likely being processed out-of-order; 
                    |the parent_vertex for the given parameter_vertex 
                    |is not defined: [ vertex_path: ${vertexPath} ]""".flatten()
            )
        }
    }

    private fun moreThanOneDataSourceFoundExceptionSupplier(
        vertexPath: SchematicPath
    ): () -> MaterializerException {
        return { ->
            MaterializerException(
                MaterializerErrorResponse.UNEXPECTED_ERROR,
                """more than one data_source found for vertex: 
                    |[ vertex_path: ${vertexPath} ]; 
                    |currently unable to handle more than 
                    |one data_source for a source_vertex""".flatten()
            )
        }
    }

    private fun dataSourceNotFoundExceptionSupplier(
        dataSourceKey: DataSource.Key<*>,
        availableDataSourceKeys: ImmutableSet<DataSource.Key<*>>
    ): () -> MaterializerException {
        return { ->
            val dataSourceKeysAvailable =
                availableDataSourceKeys
                    .asSequence()
                    .joinToString(
                        separator = ", ",
                        prefix = "{ ",
                        postfix = " }",
                        transform = { d -> "${d.name}: ${d.dataSourceType}" }
                    )
            MaterializerException(
                MaterializerErrorResponse.UNEXPECTED_ERROR,
                """no data_source found in metamodel mapping to 
                    |data_source.key: 
                    |[ expected: ${dataSourceKey}, 
                    |actual: ${dataSourceKeysAvailable} 
                    |]""".flatten()
            )
        }
    }
}
