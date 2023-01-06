package funcify.feature.graph.behavior

import funcify.feature.graph.data.GraphData
import funcify.feature.graph.data.ParallelizableEdgeDirectedGraphData
import funcify.feature.graph.data.ParallelizableEdgeDirectedGraphData.Companion.ParallelizableEdgeDirectedGraphWT
import funcify.feature.graph.data.ParallelizableEdgeDirectedGraphData.Companion.narrowed
import funcify.feature.graph.line.DirectedLine
import funcify.feature.graph.line.Line
import java.util.stream.Stream
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf

internal interface ParallelizableEdgeDirectedGraphBehavior :
    DirectedGraphBehavior<ParallelizableEdgeDirectedGraphWT> {

    override fun <P, V, E> empty(): GraphData<ParallelizableEdgeDirectedGraphWT, P, V, E> {
        return ParallelizableEdgeDirectedGraphData.empty()
    }

    override fun <P, V, E> verticesByPoint(
        container: GraphData<ParallelizableEdgeDirectedGraphWT, P, V, E>
    ): Map<P, V> {
        return container.narrowed().verticesByPoint
    }

    override fun <P, V, E> streamEdges(
        container: GraphData<ParallelizableEdgeDirectedGraphWT, P, V, E>
    ): Stream<out Pair<Line<P>, E>> {
        return container.narrowed().edgesSetByLine.entries.stream().flatMap {
            (l: Line<P>, es: PersistentSet<E>) ->
            es.stream().map { e: E -> l to e }
        }
    }

    override fun <P, V, E> get(
        container: GraphData<ParallelizableEdgeDirectedGraphWT, P, V, E>,
        line: Line<P>
    ): Iterable<E> {
        return when (line) {
            is DirectedLine -> {
                container.narrowed().edgesSetByLine[line] ?: persistentSetOf()
            }
            else -> {
                persistentSetOf()
            }
        }
    }

    override fun <P, V, E> put(
        container: GraphData<ParallelizableEdgeDirectedGraphWT, P, V, E>,
        point: P,
        vertex: V,
    ): GraphData<ParallelizableEdgeDirectedGraphWT, P, V, E> {
        return container
            .narrowed()
            .copy(verticesByPoint = container.narrowed().verticesByPoint.put(point, vertex))
    }

    override fun <P, V, E> put(
        container: GraphData<ParallelizableEdgeDirectedGraphWT, P, V, E>,
        line: Line<P>,
        edge: E,
    ): GraphData<ParallelizableEdgeDirectedGraphWT, P, V, E> {
        val verticesByPoint = container.narrowed().verticesByPoint
        val (point1, point2) = line
        return if (line is DirectedLine && point1 in verticesByPoint && point2 in verticesByPoint) {
            container
                .narrowed()
                .copy(
                    edgesSetByLine =
                        container
                            .narrowed()
                            .edgesSetByLine
                            .put(
                                line,
                                container
                                    .narrowed()
                                    .edgesSetByLine
                                    .getOrElse(line) { -> persistentSetOf() }
                                    .add(edge)
                            )
                )
        } else {
            container
        }
    }

    override fun <P, V, E> removeVertex(
        container: GraphData<ParallelizableEdgeDirectedGraphWT, P, V, E>,
        point: P,
    ): GraphData<ParallelizableEdgeDirectedGraphWT, P, V, E> {
        return putAllEdges(
            putAllVertices(empty(), container.narrowed().verticesByPoint.remove(point)),
            streamEdges(container)
        )
    }

    override fun <P, V, E> removeEdges(
        container: GraphData<ParallelizableEdgeDirectedGraphWT, P, V, E>,
        line: Line<P>,
    ): GraphData<ParallelizableEdgeDirectedGraphWT, P, V, E> {
        return if (line is DirectedLine) {
            container
                .narrowed()
                .copy(edgesSetByLine = container.narrowed().edgesSetByLine.remove(line))
        } else {
            container
        }
    }

    override fun <P, V, E> successorsAsStream(
        container: GraphData<ParallelizableEdgeDirectedGraphWT, P, V, E>,
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
