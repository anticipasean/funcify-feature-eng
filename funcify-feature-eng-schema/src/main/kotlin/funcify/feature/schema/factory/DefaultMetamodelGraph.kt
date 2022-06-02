package funcify.feature.schema.factory

import funcify.feature.schema.MetamodelGraph
import funcify.feature.schema.SchematicEdge
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.graph.PathBasedGraph
import kotlinx.collections.immutable.PersistentMap

/**
 *
 * @author smccarron
 * @created 4/2/22
 */
internal data class DefaultMetamodelGraph(
    override val dataSourcesByKey: PersistentMap<DataSource.Key<*>, DataSource<*>>,
    private val pathBasedGraph: PathBasedGraph<SchematicPath, SchematicVertex, SchematicEdge> =
        PathBasedGraph.emptyTwoToOnePathsToEdgeGraph(),
) :
    PathBasedGraph<SchematicPath, SchematicVertex, SchematicEdge> by pathBasedGraph,
    MetamodelGraph {
    companion object {}
}
