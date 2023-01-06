package funcify.feature.graph.behavior

import funcify.feature.graph.data.GraphData
import funcify.feature.graph.data.UndirectedGraphData
import funcify.feature.graph.data.UndirectedGraphData.Companion.UndirectedGraphDataWT
import funcify.feature.graph.data.UndirectedGraphData.Companion.narrowed
import funcify.feature.graph.line.Line
import funcify.feature.graph.line.UndirectedLine
import java.util.stream.Stream
import kotlinx.collections.immutable.persistentSetOf

/**
 *
 * @author smccarron
 * @created 2023-01-06
 */
internal interface UndirectedGraphBehavior : GraphBehavior<UndirectedGraphDataWT> {

    override fun <P, V, E> empty(): GraphData<UndirectedGraphDataWT, P, V, E> {
        return UndirectedGraphData.empty<P, V, E>()
    }

    override fun <P> line(firstOrSource: P, secondOrDestination: P): Line<P> {
        return UndirectedLine.of(firstOrSource, secondOrDestination)
    }

    override fun <P, V, E> verticesByPoint(
        container: GraphData<UndirectedGraphDataWT, P, V, E>
    ): Map<P, V> {
        return container.narrowed().verticesByPoint
    }

    override fun <P, V, E> streamEdges(
        container: GraphData<UndirectedGraphDataWT, P, V, E>
    ): Stream<out Pair<Line<P>, E>> {
        return container.narrowed().edgesByLine.entries.stream().map { (l: UndirectedLine<P>, e: E)
            ->
            l to e
        }
    }

    override fun <P, V, E> get(
        container: GraphData<UndirectedGraphDataWT, P, V, E>,
        line: Line<P>
    ): Iterable<E> {
        return when (line) {
            is UndirectedLine -> {
                container.narrowed().edgesByLine[line]?.let { e: E -> persistentSetOf(e) }
                    ?: persistentSetOf()
            }
            else -> {
                persistentSetOf()
            }
        }
    }

    override fun <P, V, E> put(
        container: GraphData<UndirectedGraphDataWT, P, V, E>,
        point: P,
        vertex: V
    ): GraphData<UndirectedGraphDataWT, P, V, E> {
        return container
            .narrowed()
            .copy(verticesByPoint = container.narrowed().verticesByPoint.put(point, vertex))
    }

    override fun <P, V, E> put(
        container: GraphData<UndirectedGraphDataWT, P, V, E>,
        line: Line<P>,
        edge: E,
    ): GraphData<UndirectedGraphDataWT, P, V, E> {
        val verticesByPoint = container.narrowed().verticesByPoint
        val (p1, p2) = line
        return if (line is UndirectedLine && p1 in verticesByPoint && p2 in verticesByPoint) {
            container
                .narrowed()
                .copy(edgesByLine = container.narrowed().edgesByLine.put(line, edge))
        } else {
            container
        }
    }

    override fun <P, V, E> removeVertex(
        container: GraphData<UndirectedGraphDataWT, P, V, E>,
        point: P
    ): GraphData<UndirectedGraphDataWT, P, V, E> {
        return putAllEdges(
            putAllVertices(empty(), container.narrowed().verticesByPoint.remove(point)),
            streamEdges(container)
        )
    }

    override fun <P, V, E> removeEdges(
        container: GraphData<UndirectedGraphDataWT, P, V, E>,
        line: Line<P>,
    ): GraphData<UndirectedGraphDataWT, P, V, E> {
        return when (line) {
            is UndirectedLine -> {
                container
                    .narrowed()
                    .copy(edgesByLine = container.narrowed().edgesByLine.remove(line))
            }
            else -> {
                container
            }
        }
    }
}
