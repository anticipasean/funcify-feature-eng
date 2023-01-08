package funcify.feature.graph.behavior

import funcify.feature.graph.GraphDescriptor
import funcify.feature.graph.data.GraphData
import funcify.feature.graph.data.ParallelizableEdgeUndirectedGraphData.Companion.ParallelizableEdgeUndirectedGraphWT
import funcify.feature.graph.line.Line
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/**
 *
 * @author smccarron
 * @created 2023-01-06
 */
internal interface SelfLoopingParallelizableEdgeUndirectedGraphBehavior :
    ParallelizableEdgeUndirectedGraphBehavior {

    override fun descriptors(): ImmutableSet<GraphDescriptor> {
        return persistentSetOf(
            GraphDescriptor.PERMIT_SELF_LOOPS,
            GraphDescriptor.PERMIT_PARALLEL_EDGES
        )
    }

    override fun <P, V, E> includeEdge(
        container: GraphData<ParallelizableEdgeUndirectedGraphWT, P, V, E>,
        line: Line<P>,
        edge: E
    ): Boolean {
        val (p1: P, p2: P) = line
        val verticesByPoint: Map<P, V> = verticesByPoint(container)
        return p1 in verticesByPoint && p2 in verticesByPoint
    }
}
