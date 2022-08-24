package funcify.feature.materializer.service

import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession
import funcify.feature.materializer.schema.RequestParameterEdge
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.deferred.Deferred
import funcify.feature.tools.container.graph.PathBasedGraph
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import kotlin.streams.asSequence
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.slf4j.Logger

internal class DefaultSingleRequestMaterializationPreprocessingService :
    SingleRequestMaterializationPreprocessingService {

    companion object {
        private val logger: Logger =
            loggerFor<DefaultSingleRequestMaterializationPreprocessingService>()
        //        private data class SubgraphingContext()
    }

    override fun preprocessRequestMaterializationGraphInSession(
        session: SingleRequestFieldMaterializationSession
    ): Deferred<SingleRequestFieldMaterializationSession> {
        logger.info(
            "preprocess_request_materialization_graph_in_session: [ session.session_id: ${session.sessionId} ]"
        )
        if (
            session.requestMaterializationGraph.vertices.isEmpty() ||
                session.requestMaterializationGraph.edgesByConnectedVertices.isEmpty()
        ) {
            logger.error(
                """preprocess_request_materialization_graph_in_session: 
                |[ status: failed ] 
                |session has not been updated with a 
                |request_materialization_graph; 
                |a key processing step has been skipped!""".flatten()
            )
            return Deferred.failed(
                MaterializerException(
                    MaterializerErrorResponse.UNEXPECTED_ERROR,
                    """materialization_processing_step: 
                        |[ request_materialization_graph_creation ] 
                        |has been skipped""".flatten()
                )
            )
        }
        var entryIndex: Int = 0
        createSubgraphsLinkedToRetrievalFunctionSpecEdges(session.requestMaterializationGraph)
            .forEach { (edgeId, subgraph) ->
                logger.info(
                    "[${entryIndex++}]: edge_id: ${edgeId.first} --> ${edgeId.second}\n{}\n",
                    createGraphStr(subgraph)
                )
            }

        return Deferred.completed(session)
    }

    private fun createGraphStr(
        graph: PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>
    ): String {
        val edgeToString: (RequestParameterEdge) -> String = { e ->
            StandardNamingConventions.SNAKE_CASE.deriveName(e::class.simpleName!!).qualifiedForm +
                "(" +
                (when (e) {
                    is RequestParameterEdge.RetrievalFunctionSpecRequestParameterEdge -> {
                        "datasource.key.name: " +
                            e.dataSource.key.name +
                            ", source_vertices.keys: " +
                            e.sourceVerticesByPath.keys
                                .asSequence()
                                .joinToString(", ", "{ ", " }") +
                            ", parameter_vertices.keys: " +
                            e.parameterVerticesByPath.keys
                                .asSequence()
                                .joinToString(", ", "{ ", " }")
                    }
                    is RequestParameterEdge.DependentValueRequestParameterEdge -> {
                        ""
                    }
                    is RequestParameterEdge.MaterializedValueRequestParameterEdge -> {
                        ""
                    }
                    else -> ""
                }) +
                ")"
        }
        return "%s\n%s".format(
            graph.verticesByPath.keys
                .asSequence()
                .joinToString(separator = ",\n", prefix = "vertices: { ", postfix = " }"),
            graph
                .edgesAsStream()
                .asSequence()
                .joinToString(
                    separator = ",\n",
                    prefix = "edges: { ",
                    postfix = " }",
                    transform = { e ->
                        "%s --> %s --> %s".format(e.id.first, edgeToString.invoke(e), e.id.second)
                    }
                )
        )
    }

    private fun createSubgraphsLinkedToRetrievalFunctionSpecEdges(
        requestMaterializationGraph:
            PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>
    ): PersistentMap<
        Pair<SchematicPath, SchematicPath>,
        PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>
    > {
        return requestMaterializationGraph
            .edgesAsStream()
            .parallel()
            .filter { rpe: RequestParameterEdge ->
                rpe is RequestParameterEdge.RetrievalFunctionSpecRequestParameterEdge
            }
            .map { rpe: RequestParameterEdge ->
                rpe as RequestParameterEdge.RetrievalFunctionSpecRequestParameterEdge
            }
            .flatMap { rfspe: RequestParameterEdge.RetrievalFunctionSpecRequestParameterEdge ->
                requestMaterializationGraph.depthFirstSearchOnPath(rfspe.id.second).map { tuple ->
                    rfspe.id to tuple
                }
            }
            .reduce(
                persistentMapOf<
                    Pair<SchematicPath, SchematicPath>,
                    PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>
                >(),
                { pm, (specEdgeId, tuple) ->
                    pm.put(
                        specEdgeId,
                        pm.getOrElse(specEdgeId) { PathBasedGraph.emptyTwoToOnePathsToEdgeGraph() }
                            .putVertex(tuple.second, tuple.first)
                            .putVertex(tuple.fourth, tuple.fifth)
                            .putEdge(tuple.third, RequestParameterEdge::id)
                    )
                },
                { pm1, pm2 ->
                    val finalResultHolder:
                        Array<
                            PersistentMap<
                                Pair<SchematicPath, SchematicPath>,
                                PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>
                            >
                        > =
                        arrayOf(pm2)
                    pm1.forEach { (specEdgeId, subgraph) ->
                        finalResultHolder[0] =
                            finalResultHolder[0].put(
                                specEdgeId,
                                subgraph.fold(
                                    { vs, es ->
                                        finalResultHolder[0]
                                            .getOrElse(specEdgeId) {
                                                PathBasedGraph.emptyTwoToOnePathsToEdgeGraph()
                                            }
                                            .putAllVertices(vs)
                                            .putAllEdges(es)
                                    },
                                    { vs, eSets ->
                                        finalResultHolder[0]
                                            .getOrElse(specEdgeId) {
                                                PathBasedGraph.emptyTwoToOnePathsToEdgeGraph()
                                            }
                                            .putAllVertices(vs)
                                            .putAllEdgeSets(eSets)
                                    }
                                )
                            )
                    }
                    finalResultHolder[0]
                }
            )
    }
}
