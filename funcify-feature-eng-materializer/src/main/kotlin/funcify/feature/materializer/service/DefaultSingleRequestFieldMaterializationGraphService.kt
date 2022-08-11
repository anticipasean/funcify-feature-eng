package funcify.feature.materializer.service

import com.google.common.cache.CacheBuilder
import funcify.feature.materializer.error.MaterializerErrorResponse
import funcify.feature.materializer.error.MaterializerException
import funcify.feature.materializer.fetcher.SingleRequestFieldMaterializationSession
import funcify.feature.schema.SchematicEdge
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.deferred.Deferred
import funcify.feature.tools.container.graph.PathBasedGraph
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.execution.ResultPath
import java.util.concurrent.ConcurrentMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-08-08
 */
internal class DefaultSingleRequestFieldMaterializationGraphService :
    SingleRequestFieldMaterializationGraphService {

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
            .map { (sourceJunctionVertex, parameterVerticesSet) ->
                createInitialEdgesInGraphMappingParameterVerticesToSourceJunctionVertex(
                    session,
                    sourceJunctionVertex,
                    parameterVerticesSet
                )
            }

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

    private fun createInitialEdgesInGraphMappingParameterVerticesToSourceJunctionVertex(
        session: SingleRequestFieldMaterializationSession,
        sourceJunctionVertex: SchematicVertex,
        parameterVertexPaths: ImmutableSet<SchematicVertex>,
    ): PathBasedGraph<SchematicPath, SchematicVertex, SchematicEdge> {
        TODO("Not yet implemented")
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
}
