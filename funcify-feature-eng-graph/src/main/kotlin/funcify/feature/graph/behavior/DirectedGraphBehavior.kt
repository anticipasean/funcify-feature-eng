package funcify.feature.graph.behavior

import funcify.feature.graph.data.DirectedGraphData
import funcify.feature.graph.data.DirectedGraphData.Companion.DirectedGraphWT
import funcify.feature.graph.data.DirectedGraphData.Companion.narrowed
import funcify.feature.graph.data.GraphData
import funcify.feature.graph.line.DirectedLine
import funcify.feature.graph.line.Line
import java.util.stream.Stream
import kotlinx.collections.immutable.persistentSetOf

internal interface DirectedGraphBehavior : GraphBehavior<DirectedGraphWT> {

    override fun <P, V, E> empty(): GraphData<DirectedGraphWT, P, V, E> {
        return DirectedGraphData.empty()
    }

    override fun <P> line(firstOrSource: P, secondOrDestination: P): Line<P> {
        return DirectedLine.of(firstOrSource, secondOrDestination)
    }

    override fun <P, V, E> get(
        container: GraphData<DirectedGraphWT, P, V, E>,
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
        container: GraphData<DirectedGraphWT, P, V, E>,
        point: P,
        vertex: V
    ): GraphData<DirectedGraphWT, P, V, E> {
        return container
            .narrowed()
            .copy(verticesByPoint = container.narrowed().verticesByPoint.put(point, vertex))
    }

    override fun <P, V, E> put(
        container: GraphData<DirectedGraphWT, P, V, E>,
        line: Line<P>,
        edge: E
    ): GraphData<DirectedGraphWT, P, V, E> {
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
        container: GraphData<DirectedGraphWT, P, V, E>,
        point: P
    ): GraphData<DirectedGraphWT, P, V, E> {
        return putAllEdges(
            putAllVertices(empty(), container.narrowed().verticesByPoint.remove(point)),
            streamEdges(container)
        )
    }

    override fun <P, V, E> removeEdges(
        container: GraphData<DirectedGraphWT, P, V, E>,
        line: Line<P>
    ): GraphData<DirectedGraphWT, P, V, E> {
        return if (line is DirectedLine) {
            container.narrowed().copy(edgesByLine = container.narrowed().edgesByLine.remove(line))
        } else {
            container
        }
    }

    override fun <P, V, E> verticesByPoint(
        container: GraphData<DirectedGraphWT, P, V, E>
    ): Map<P, V> {
        return container.narrowed().verticesByPoint
    }

    override fun <P, V, E> streamEdges(
        container: GraphData<DirectedGraphWT, P, V, E>
    ): Stream<out Pair<Line<P>, E>> {
        return container.narrowed().edgesByLine.entries.stream().map { (l: DirectedLine<P>, e: E) ->
            l to e
        }
    }
}
