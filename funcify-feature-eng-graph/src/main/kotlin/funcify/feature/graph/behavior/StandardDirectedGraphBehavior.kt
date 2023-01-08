package funcify.feature.graph.behavior

import funcify.feature.graph.GraphDescriptor
import funcify.feature.graph.data.GraphData
import funcify.feature.graph.data.StandardDirectedGraphData
import funcify.feature.graph.data.StandardDirectedGraphData.Companion.StandardDirectedGraphWT
import funcify.feature.graph.data.StandardDirectedGraphData.Companion.narrowed
import funcify.feature.graph.line.DirectedLine
import funcify.feature.graph.line.Line
import kotlinx.collections.immutable.ImmutableSet
import java.util.stream.Stream
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentMap

internal interface StandardDirectedGraphBehavior : DirectedGraphBehavior<StandardDirectedGraphWT> {

    override fun <P, V, E> empty(): GraphData<StandardDirectedGraphWT, P, V, E> {
        return StandardDirectedGraphData.empty()
    }

    override fun <P, V, E, M : Map<P, V>> of(
        verticesByPoint: M
    ): GraphData<StandardDirectedGraphWT, P, V, E> {
        return StandardDirectedGraphData(
            verticesByPoint = verticesByPoint.toPersistentMap(),
            edgesByLine = persistentMapOf()
        )
    }

    override fun descriptors(): ImmutableSet<GraphDescriptor> {
        return persistentSetOf(GraphDescriptor.DIRECTED)
    }

    override fun <P, V, E> get(
        container: GraphData<StandardDirectedGraphWT, P, V, E>,
        line: Line<P>
                              ): Iterable<E> {
        return when (line) {
            is DirectedLine -> {
                when (val e: E? = container.narrowed().edgesByLine[line]) {
                    null -> persistentSetOf()
                    else -> persistentSetOf(e)
                }
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
        return if (includeEdge(container, line, edge) && line is DirectedLine) {
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

    override fun <P, V, E> edgeCount(container: GraphData<StandardDirectedGraphWT, P, V, E>): Int {
        return container.narrowed().edgesByLine.size
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
        container: GraphData<StandardDirectedGraphWT, P, V, E>,
        point: P
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
        container: GraphData<StandardDirectedGraphWT, P, V, E>,
        point: P
    ): Stream<out Pair<Line<P>, E>> {
        return when (val pts: PersistentSet<P>? = container.narrowed().outgoingLines[point]) {
            null -> {
                Stream.empty()
            }
            else -> {
                pts.stream().flatMap { p: P ->
                    val line = line(point, p)
                    when (val edge: E? = container.narrowed().edgesByLine[line]) {
                        null -> {
                            Stream.empty()
                        }
                        else -> {
                            Stream.of(line to edge)
                        }
                    }
                }
            }
        }
    }

    override fun <P, V, E> edgesToPointAsStream(
        container: GraphData<StandardDirectedGraphWT, P, V, E>,
        point: P
    ): Stream<out Pair<Line<P>, E>> {
        return when (val pts: PersistentSet<P>? = container.narrowed().incomingLines[point]) {
            null -> {
                Stream.empty()
            }
            else -> {
                pts.stream().flatMap { p: P ->
                    val line = line(p, point)
                    when (val edge: E? = container.narrowed().edgesByLine[line]) {
                        null -> {
                            Stream.empty()
                        }
                        else -> {
                            Stream.of(line to edge)
                        }
                    }
                }
            }
        }
    }
}
