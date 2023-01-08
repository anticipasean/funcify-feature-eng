package funcify.feature.graph.behavior

import funcify.feature.graph.data.GraphData
import funcify.feature.graph.data.ParallelizableEdgeDirectedGraphData
import funcify.feature.graph.data.ParallelizableEdgeDirectedGraphData.Companion.ParallelizableEdgeDirectedGraphWT
import funcify.feature.graph.data.ParallelizableEdgeDirectedGraphData.Companion.narrowed
import funcify.feature.graph.line.DirectedLine
import funcify.feature.graph.line.Line
import java.util.stream.Stream
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentMap

internal interface ParallelizableEdgeDirectedGraphBehavior :
    DirectedGraphBehavior<ParallelizableEdgeDirectedGraphWT> {

    override fun <P, V, E> empty(): GraphData<ParallelizableEdgeDirectedGraphWT, P, V, E> {
        return ParallelizableEdgeDirectedGraphData.empty()
    }

    override fun <P, V, E, M : Map<P, V>> of(
        verticesByPoint: M
    ): GraphData<ParallelizableEdgeDirectedGraphWT, P, V, E> {
        return ParallelizableEdgeDirectedGraphData(
            verticesByPoint = verticesByPoint.toPersistentMap(),
            edgesSetByLine = persistentMapOf()
        )
    }

    override fun <P, V, E> verticesByPoint(
        container: GraphData<ParallelizableEdgeDirectedGraphWT, P, V, E>
    ): Map<P, V> {
        return container.narrowed().verticesByPoint
    }

    override fun <P, V, E> edgeCount(
        container: GraphData<ParallelizableEdgeDirectedGraphWT, P, V, E>
    ): Int {
        return container
            .narrowed()
            .edgesSetByLine
            .entries
            .parallelStream()
            .mapToInt { (_: DirectedLine<P>, es: PersistentSet<E>) -> es.size }
            .sum()
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
        return if (includeEdge(container, line, edge) && line is DirectedLine) {
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
        return when (val pts: PersistentSet<P>? = container.narrowed().outgoingLines[point]) {
            null -> {
                Stream.empty()
            }
            else -> {
                pts.stream().flatMap { p: P ->
                    when (val v: V? = container.narrowed().verticesByPoint[p]) {
                        null -> Stream.empty()
                        else -> Stream.of(p to v)
                    }
                }
            }
        }
    }

    override fun <P, V, E> predecessorVerticesAsStream(
        container: GraphData<ParallelizableEdgeDirectedGraphWT, P, V, E>,
        point: P,
    ): Stream<out Pair<P, V>> {
        return when (val pts: PersistentSet<P>? = container.narrowed().incomingLines[point]) {
            null -> {
                Stream.empty()
            }
            else -> {
                pts.stream().flatMap { p: P ->
                    when (val v: V? = container.narrowed().verticesByPoint[p]) {
                        null -> Stream.empty()
                        else -> Stream.of(p to v)
                    }
                }
            }
        }
    }

    override fun <P, V, E> edgesFromPointAsStream(
        container: GraphData<ParallelizableEdgeDirectedGraphWT, P, V, E>,
        point: P,
    ): Stream<out Pair<Line<P>, E>> {
        return when (val pts: PersistentSet<P>? = container.narrowed().outgoingLines[point]) {
            null -> {
                Stream.empty()
            }
            else -> {
                pts.stream().flatMap { p: P ->
                    val line = line(point, p)
                    when (
                        val edges: PersistentSet<E>? = container.narrowed().edgesSetByLine[line]
                    ) {
                        null -> {
                            Stream.empty()
                        }
                        else -> {
                            edges.stream().map { e: E -> line to e }
                        }
                    }
                }
            }
        }
    }

    override fun <P, V, E> edgesToPointAsStream(
        container: GraphData<ParallelizableEdgeDirectedGraphWT, P, V, E>,
        point: P,
    ): Stream<out Pair<Line<P>, E>> {
        return when (val pts: PersistentSet<P>? = container.narrowed().incomingLines[point]) {
            null -> {
                Stream.empty()
            }
            else -> {
                pts.stream().flatMap { p: P ->
                    val line = line(p, point)
                    when (
                        val edges: PersistentSet<E>? = container.narrowed().edgesSetByLine[line]
                    ) {
                        null -> {
                            Stream.empty()
                        }
                        else -> {
                            edges.stream().map { e: E -> line to e }
                        }
                    }
                }
            }
        }
    }
}
