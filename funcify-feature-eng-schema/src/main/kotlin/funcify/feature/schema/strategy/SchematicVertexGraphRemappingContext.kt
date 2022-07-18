package funcify.feature.schema.strategy

import funcify.feature.schema.SchematicEdge
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.factory.SchematicVertexFactory
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.graph.PathBasedGraph
import kotlinx.collections.immutable.ImmutableMap

/**
 *
 * @author smccarron
 * @created 2022-07-18
 */
interface SchematicVertexGraphRemappingContext {

    val dataSourcesByKey: ImmutableMap<DataSource.Key<*>, DataSource<*>>

    val schematicVertexFactory: SchematicVertexFactory

    val pathBasedGraph: PathBasedGraph<SchematicPath, SchematicVertex, SchematicEdge>

    fun updateGraph(
        graph: PathBasedGraph<SchematicPath, SchematicVertex, SchematicEdge>
    ): SchematicVertexGraphRemappingContext
}
