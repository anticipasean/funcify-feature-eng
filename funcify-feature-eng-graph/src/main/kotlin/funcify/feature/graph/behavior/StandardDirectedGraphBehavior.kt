package funcify.feature.graph.behavior

import funcify.feature.graph.data.GraphData
import funcify.feature.graph.data.StandardDirectedGraphData
import funcify.feature.graph.data.StandardDirectedGraphData.Companion.StandardDirectedGraphWT
import funcify.feature.graph.data.StandardDirectedGraphData.Companion.narrowed
import funcify.feature.graph.line.DirectedLine
import funcify.feature.graph.line.Line
import java.util.stream.Stream
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf

internal interface StandardDirectedGraphBehavior : DirectedGraphBehavior<StandardDirectedGraphWT> {

    override fun <P, V, E> empty(): GraphData<StandardDirectedGraphWT, P, V, E> {
        return StandardDirectedGraphData.empty()
    }

    override fun <P, V, E> get(
        container: GraphData<StandardDirectedGraphWT, P, V, E>,
        line: Line<P>
    ): Iterable<E> {
        return when (line) {
            is DirectedLine -> {
                container.narrowed().edgesByLine[line]?.let { e: E -> persistentSetOf(e) }
                    ?: persistentSetOf()
            }
            else -> {
                persistentSetOf()
            }
        }
    }

    override fun <P, V, E> put(
        container: GraphData<StandardDirectedGraphWT, P, V, E>,
        point: P,
        vertex: V
    ): GraphData<StandardDirectedGraphWT, P, V, E> {
        return container
            .narrowed()
            .copy(verticesByPoint = container.narrowed().verticesByPoint.put(point, vertex))
    }

    override fun <P, V, E> put(
        container: GraphData<StandardDirectedGraphWT, P, V, E>,
        line: Line<P>,
        edge: E
    ): GraphData<StandardDirectedGraphWT, P, V, E> {
        val verticesByPath = container.narrowed().verticesByPoint
        val (point1, point2) = line
        return if (line is DirectedLine && point1 in verticesByPath && point2 in verticesByPath) {
            container
                .narrowed()
                .copy(edgesByLine = container.narrowed().edgesByLine.put(line, edge))
        } else {
            container
        }
    }

    override fun <P, V, E> removeVertex(
        container: GraphData<StandardDirectedGraphWT, P, V, E>,
        point: P
    ): GraphData<StandardDirectedGraphWT, P, V, E> {
        return putAllEdges(
            putAllVertices(empty(), container.narrowed().verticesByPoint.remove(point)),
            streamEdges(container)
        )
    }

    override fun <P, V, E> removeEdges(
        container: GraphData<StandardDirectedGraphWT, P, V, E>,
        line: Line<P>
    ): GraphData<StandardDirectedGraphWT, P, V, E> {
        return if (line is DirectedLine) {
            container.narrowed().copy(edgesByLine = container.narrowed().edgesByLine.remove(line))
        } else {
            container
        }
    }

    override fun <P, V, E> verticesByPoint(
        container: GraphData<StandardDirectedGraphWT, P, V, E>
    ): Map<P, V> {
        return container.narrowed().verticesByPoint
    }

    override fun <P, V, E> streamEdges(
        container: GraphData<StandardDirectedGraphWT, P, V, E>
    ): Stream<out Pair<Line<P>, E>> {
        return container.narrowed().edgesByLine.entries.stream().map { (l: DirectedLine<P>, e: E) ->
            l to e
        }
    }

    override fun <P, V, E> successorsAsStream(
        container: GraphData<StandardDirectedGraphWT, P, V, E>,
        point: P
    ): Stream<out Pair<P, V>> {
        return container.narrowed().outgoingLines[point]?.let { points: PersistentSet<P> ->
            points.stream().flatMap { p: P ->
                get(container, p)?.let { v: V -> Stream.of(p to v) } ?: Stream.empty()
            }
        }
            ?: Stream.empty()
    }
}
