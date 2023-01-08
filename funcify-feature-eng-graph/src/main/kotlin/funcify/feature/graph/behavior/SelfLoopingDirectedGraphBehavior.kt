package funcify.feature.graph.behavior

import funcify.feature.graph.GraphDescriptor
import funcify.feature.graph.data.GraphData
import funcify.feature.graph.data.StandardDirectedGraphData.Companion.StandardDirectedGraphWT
import funcify.feature.graph.line.Line
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

internal interface SelfLoopingDirectedGraphBehavior : StandardDirectedGraphBehavior {

    override fun descriptors(): ImmutableSet<GraphDescriptor> {
        return persistentSetOf(GraphDescriptor.DIRECTED, GraphDescriptor.PERMIT_SELF_LOOPS)
    }

    override fun <P, V, E> includeEdge(
        container: GraphData<StandardDirectedGraphWT, P, V, E>,
        line: Line<P>,
        edge: E
    ): Boolean {
        val (p1: P, p2: P) = line
        val verticesByPoint: Map<P, V> = verticesByPoint(container)
        return p1 in verticesByPoint && p2 in verticesByPoint
    }
}
