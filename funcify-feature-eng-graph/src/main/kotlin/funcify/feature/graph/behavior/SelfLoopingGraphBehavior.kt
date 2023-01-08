package funcify.feature.graph.behavior

import funcify.feature.graph.data.GraphData
import funcify.feature.graph.line.Line

/**
 *
 * @author smccarron
 * @created 2023-01-08
 */
internal interface SelfLoopingGraphBehavior<DWT> : GraphBehavior<DWT> {

    override fun <P, V, E> includeEdge(
        container: GraphData<DWT, P, V, E>,
        line: Line<P>,
        edge: E
    ): Boolean {
        val (p1: P, p2: P) = line
        val verticesByPoint: Map<P, V> = verticesByPoint(container)
        return p1 in verticesByPoint && p2 in verticesByPoint
    }
}
