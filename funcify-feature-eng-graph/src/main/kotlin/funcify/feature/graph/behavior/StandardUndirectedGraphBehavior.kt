package funcify.feature.graph.behavior

import funcify.feature.graph.GraphDescriptor
import funcify.feature.graph.data.GraphData
import funcify.feature.graph.data.StandardUndirectedGraphData
import funcify.feature.graph.data.StandardUndirectedGraphData.Companion.StandardUndirectedGraphDataWT
import funcify.feature.graph.data.StandardUndirectedGraphData.Companion.narrowed
import funcify.feature.graph.line.Line
import funcify.feature.graph.line.UndirectedLine
import kotlinx.collections.immutable.ImmutableSet
import java.util.stream.Stream
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentMap

/**
 *
 * @author smccarron
 * @created 2023-01-06
 */
internal interface StandardUndirectedGraphBehavior :
    UndirectedGraphBehavior<StandardUndirectedGraphDataWT> {

    override fun <P, V, E> empty(): GraphData<StandardUndirectedGraphDataWT, P, V, E> {
        return StandardUndirectedGraphData.empty<P, V, E>()
    }

    override fun <P, V, E, M : Map<P, V>> of(
        verticesByPoint: M
    ): GraphData<StandardUndirectedGraphDataWT, P, V, E> {
        return StandardUndirectedGraphData(
            verticesByPoint = verticesByPoint.toPersistentMap(),
            edgesByLine = persistentMapOf()
        )
    }

    override fun descriptors(): ImmutableSet<GraphDescriptor> {
        return persistentSetOf()
    }

    override fun <P, V, E> verticesByPoint(
        container: GraphData<StandardUndirectedGraphDataWT, P, V, E>
                                          ): Map<P, V> {
        return container.narrowed().verticesByPoint
    }

    override fun <P, V, E> edgeCount(
        container: GraphData<StandardUndirectedGraphDataWT, P, V, E>
    ): Int {
        return container.narrowed().edgesByLine.size
    }

    override fun <P, V, E> streamEdges(
        container: GraphData<StandardUndirectedGraphDataWT, P, V, E>
    ): Stream<out Pair<Line<P>, E>> {
        return container.narrowed().edgesByLine.entries.stream().map { (l: UndirectedLine<P>, e: E)
            ->
            l to e
        }
    }

    override fun <P, V, E> get(
        container: GraphData<StandardUndirectedGraphDataWT, P, V, E>,
        line: Line<P>
    ): Iterable<E> {
        return when (line) {
            is UndirectedLine -> {
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
        container: GraphData<StandardUndirectedGraphDataWT, P, V, E>,
        point: P,
        vertex: V
    ): GraphData<StandardUndirectedGraphDataWT, P, V, E> {
        return container
            .narrowed()
            .copy(verticesByPoint = container.narrowed().verticesByPoint.put(point, vertex))
    }

    override fun <P, V, E> put(
        container: GraphData<StandardUndirectedGraphDataWT, P, V, E>,
        line: Line<P>,
        edge: E,
    ): GraphData<StandardUndirectedGraphDataWT, P, V, E> {
        return if (includeEdge(container, line, edge) && line is UndirectedLine) {
            container
                .narrowed()
                .copy(edgesByLine = container.narrowed().edgesByLine.put(line, edge))
        } else {
            container
        }
    }

    override fun <P, V, E> removeVertex(
        container: GraphData<StandardUndirectedGraphDataWT, P, V, E>,
        point: P
    ): GraphData<StandardUndirectedGraphDataWT, P, V, E> {
        return putAllEdges(
            putAllVertices(empty(), container.narrowed().verticesByPoint.remove(point)),
            streamEdges(container)
        )
    }

    override fun <P, V, E> removeEdges(
        container: GraphData<StandardUndirectedGraphDataWT, P, V, E>,
        line: Line<P>,
    ): GraphData<StandardUndirectedGraphDataWT, P, V, E> {
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
