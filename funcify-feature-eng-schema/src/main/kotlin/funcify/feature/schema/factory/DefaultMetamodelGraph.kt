package funcify.feature.schema.factory

import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.SchematicEdge
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.graph.PathBasedGraph

/**
 *
 * @author smccarron
 * @created 4/2/22
 */
internal data class DefaultMetamodelGraph(
    private val pathBasedGraph: PathBasedGraph<SchematicPath, SchematicVertex, SchematicEdge> =
        PathBasedGraph.emptyTwoToOnePathsToEdgeGraph()
) :
    PathBasedGraph<SchematicPath, SchematicVertex, SchematicEdge> by pathBasedGraph,
    MetamodelGraph {
    companion object {}
}
