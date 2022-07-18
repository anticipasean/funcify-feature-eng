package funcify.feature.schema.strategy

import funcify.feature.schema.SchematicEdge
import funcify.feature.schema.SchematicVertex
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.factory.SchematicVertexFactory
import funcify.feature.schema.path.SchematicPath
import funcify.feature.tools.container.graph.PathBasedGraph
import kotlinx.collections.immutable.ImmutableMap

internal data class DefaultSchematicVertexGraphRemappingContext(
    override val dataSourcesByKey: ImmutableMap<DataSource.Key<*>, DataSource<*>>,
    override val schematicVertexFactory: SchematicVertexFactory,
    override val pathBasedGraph: PathBasedGraph<SchematicPath, SchematicVertex, SchematicEdge>
) : SchematicVertexGraphRemappingContext {

    override fun updateGraph(
        graph: PathBasedGraph<SchematicPath, SchematicVertex, SchematicEdge>
    ): SchematicVertexGraphRemappingContext {
        return this.copy(pathBasedGraph = graph)
    }
}
