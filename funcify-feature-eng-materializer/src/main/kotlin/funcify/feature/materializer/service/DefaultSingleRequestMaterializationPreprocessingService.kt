package funcify.feature.materializer.service

import arrow.core.filterIsInstance
import arrow.core.firstOrNone
import funcify.feature.datasource.retrieval.MultipleSourceIndicesJsonRetrievalFunction
import funcify.feature.datasource.retrieval.SchematicPathBasedJsonRetrievalFunctionFactory
import funcify.feature.datasource.retrieval.SingleSourceIndexJsonOptionCacheRetrievalFunction
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession
import funcify.feature.materializer.schema.RequestParameterEdge
import funcify.feature.materializer.schema.RequestParameterEdge.RetrievalFunctionSpecRequestParameterEdge
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.deferred.Deferred
import funcify.feature.tools.container.graph.PathBasedGraph
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import kotlin.streams.asSequence
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.slf4j.Logger

internal class DefaultSingleRequestMaterializationPreprocessingService(
    private val schematicPathBasedJsonRetrievalFunctionFactory:
        SchematicPathBasedJsonRetrievalFunctionFactory
) : SingleRequestMaterializationPreprocessingService {

    companion object {
        private val logger: Logger =
            loggerFor<DefaultSingleRequestMaterializationPreprocessingService>()

        private data class SubgraphRequestCreationContext(
            val processedSubgraphBySpecEdgeKey:
                PersistentMap<
                    Pair<SchematicPath, SchematicPath>,
                    PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>
                > =
                persistentMapOf(),
            val multiSrcIndexFunctionBySpecEdgeKey:
                PersistentMap<
                    Pair<SchematicPath, SchematicPath>, MultipleSourceIndicesJsonRetrievalFunction
                > =
                persistentMapOf(),
            val singleSrcIndexCacheFunctionBySpecEdgeKey:
                PersistentMap<
                    Pair<SchematicPath, SchematicPath>,
                    SingleSourceIndexJsonOptionCacheRetrievalFunction
                > =
                persistentMapOf()
        )
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
            .asSequence()
            .sortedBy { (edgeKey, _) ->
                when (val firstComp = edgeKey.first.compareTo(edgeKey.first)) {
                    0 -> {
                        edgeKey.second.compareTo(edgeKey.second)
                    }
                    else -> {
                        firstComp
                    }
                }
            }
            .forEach { (specEdgeKey, subGraph) ->
                logger.info(
                    "[${entryIndex++}: edge_key: ${specEdgeKey.first} --> ${specEdgeKey.second}: \n{}",
                    createGraphStr(subGraph)
                )
            }

        //            .fold(Try.success(SubgraphRequestCreationContext())) {
        //                contextAttempt,
        //                (edgeKey, subGraph) ->
        //                contextAttempt.flatMap { context ->
        //                    updateSubgraphRequestCreationContextPerSpecEdgeKeyAndSubgraph(
        //                        session,
        //                        context,
        //                        edgeKey,
        //                        subGraph
        //                    )
        //                }
        //            }
        return Deferred.completed(session)
    }

    private fun updateSubgraphRequestCreationContextPerSpecEdgeKeyAndSubgraph(
        session: SingleRequestFieldMaterializationSession,
        context: SubgraphRequestCreationContext,
        edgeKey: Pair<SchematicPath, SchematicPath>,
        subGraph: PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>,
    ): Try<SubgraphRequestCreationContext> {
        return when {
            // case 1: spec edge is on a domain node so a top level
            // identifier parameter must have been provided by the caller
            // for materialization of domain values
            edgeKey.first.isRoot() &&
                edgeKey.second.level() == 1 &&
                edgeKey.second.arguments.isEmpty() &&
                edgeKey.second.directives.isEmpty() -> {
                session.requestMaterializationGraph
                    .getEdgesFromPathToPath(edgeKey)
                    .firstOrNone()
                    .filterIsInstance<RetrievalFunctionSpecRequestParameterEdge>()
                    .successIfDefined(retrievalFunctionSpecEdgeNotFoundExceptionSupplier(edgeKey))
                    .map { specEdge ->
                        specEdge.sourceVerticesByPath.asSequence().fold(
                            specEdge.parameterVerticesByPath.asSequence().fold(
                                schematicPathBasedJsonRetrievalFunctionFactory
                                    .multipleSourceIndicesJsonRetrievalFunctionBuilder()
                                    .dataSource(specEdge.dataSource)
                            ) { bldr, (_, paramVert) ->
                                paramVert.fold(
                                    { pjv -> bldr.addRequestParameter(pjv) },
                                    { plv -> bldr.addRequestParameter(plv) }
                                )
                            }
                        ) { bldr, (_, srcVert) ->
                            srcVert.fold(
                                { sjv -> bldr.addSourceTarget(sjv) },
                                { slv -> bldr.addSourceTarget(slv) }
                            )
                        }
                    }
                    .flatMap { builder -> builder.build() }
                    .map { multiSrcIndFunc ->
                        context.copy(
                            processedSubgraphBySpecEdgeKey =
                                context.processedSubgraphBySpecEdgeKey.put(edgeKey, subGraph),
                            multiSrcIndexFunctionBySpecEdgeKey =
                                context.multiSrcIndexFunctionBySpecEdgeKey.put(
                                    edgeKey,
                                    multiSrcIndFunc
                                )
                        )
                    }
            }
            // case 2: spec edge does not contain any nodes dependent on output of a processed
            // function

            // case 2: spec edge is not a domain node and has at least one parameter value dependent
            // on the output of another request function
            else -> {
                Try.failure(unhandledSubgraphRequestCreationCaseExceptionSupplier(edgeKey).invoke())
            }
        }
    }

    private fun retrievalFunctionSpecEdgeNotFoundExceptionSupplier(
        edgeKey: Pair<SchematicPath, SchematicPath>
    ): () -> MaterializerException {
        return { ->
            MaterializerException(
                MaterializerErrorResponse.UNEXPECTED_ERROR,
                """retrieval_function_spec_edge expected 
                    |but not found at [ edge_key: ${edgeKey} ] 
                    |in request_materialization_graph""".flatten()
            )
        }
    }

    private fun unhandledSubgraphRequestCreationCaseExceptionSupplier(
        edgeKey: Pair<SchematicPath, SchematicPath>
    ): () -> MaterializerException {
        return { ->
            MaterializerException(
                MaterializerErrorResponse.UNEXPECTED_ERROR,
                """unhandled case when processing 
                    |request_parameter_function_spec_edge with 
                    |subgraph [ edge_key: ${edgeKey} ]""".flatten()
            )
        }
    }

    private fun createGraphStr(
        graph: PathBasedGraph<SchematicPath, SchematicVertex, RequestParameterEdge>
    ): String {
        val edgeToString: (RequestParameterEdge) -> String = { e ->
            StandardNamingConventions.SNAKE_CASE.deriveName(e::class.simpleName!!).qualifiedForm +
                "(" +
                (when (e) {
                    is RetrievalFunctionSpecRequestParameterEdge -> {
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
                rpe is RetrievalFunctionSpecRequestParameterEdge
            }
            .map { rpe: RequestParameterEdge -> rpe as RetrievalFunctionSpecRequestParameterEdge }
            .flatMap { rfspe: RetrievalFunctionSpecRequestParameterEdge ->
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
