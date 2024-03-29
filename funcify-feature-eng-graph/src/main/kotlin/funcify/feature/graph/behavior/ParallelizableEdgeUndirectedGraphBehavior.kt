package funcify.feature.graph.behavior

import funcify.feature.graph.GraphDescriptor
import funcify.feature.graph.data.GraphData
import funcify.feature.graph.data.ParallelizableEdgeUndirectedGraphData
import funcify.feature.graph.data.ParallelizableEdgeUndirectedGraphData.Companion.ParallelizableEdgeUndirectedGraphWT
import funcify.feature.graph.data.ParallelizableEdgeUndirectedGraphData.Companion.narrowed
import funcify.feature.graph.line.Line
import funcify.feature.graph.line.UndirectedLine
import java.util.stream.Stream
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentMap

/**
 *
 * @author smccarron
 * @created 2023-01-06
 */
internal interface ParallelizableEdgeUndirectedGraphBehavior :
    UndirectedGraphBehavior<ParallelizableEdgeUndirectedGraphWT> {

    override fun <P, V, E> empty(): GraphData<ParallelizableEdgeUndirectedGraphWT, P, V, E> {
        return ParallelizableEdgeUndirectedGraphData.empty<P, V, E>()
    }

    override fun <P, V, E, M : Map<P, V>> of(
        verticesByPoint: M
    ): GraphData<ParallelizableEdgeUndirectedGraphWT, P, V, E> {
        return ParallelizableEdgeUndirectedGraphData(
            verticesByPoint = verticesByPoint.toPersistentMap(),
            edgesSetByLine = persistentMapOf()
        )
    }

    override fun descriptors(): ImmutableSet<GraphDescriptor> {
        return persistentSetOf(GraphDescriptor.PERMIT_PARALLEL_EDGES)
    }

    override fun <P, V, E> verticesByPoint(
        container: GraphData<ParallelizableEdgeUndirectedGraphWT, P, V, E>
    ): Map<P, V> {
        return container.narrowed().verticesByPoint
    }

    override fun <P, V, E> edgeCount(
        container: GraphData<ParallelizableEdgeUndirectedGraphWT, P, V, E>
    ): Int {
        return container
            .narrowed()
            .edgesSetByLine
            .entries
            .parallelStream()
            .mapToInt { (_: UndirectedLine<P>, es: PersistentSet<E>) -> es.size }
            .sum()
    }

    override fun <P, V, E> streamEdges(
        container: GraphData<ParallelizableEdgeUndirectedGraphWT, P, V, E>
    ): Stream<out Pair<Line<P>, E>> {
        return container.narrowed().edgesSetByLine.entries.stream().flatMap {
            (l: Line<P>, es: PersistentSet<E>) ->
            es.stream().map { e: E -> l to e }
        }
    }

    override fun <P, V, E> get(
        container: GraphData<ParallelizableEdgeUndirectedGraphWT, P, V, E>,
        line: Line<P>
    ): Iterable<E> {
        return when (line) {
            is UndirectedLine -> {
                container.narrowed().edgesSetByLine[line] ?: persistentSetOf()
            }
            else -> {
                persistentSetOf()
            }
        }
    }

    override fun <P, V, E> put(
        container: GraphData<ParallelizableEdgeUndirectedGraphWT, P, V, E>,
        point: P,
        vertex: V,
    ): GraphData<ParallelizableEdgeUndirectedGraphWT, P, V, E> {
        return container
            .narrowed()
            .copy(verticesByPoint = container.narrowed().verticesByPoint.put(point, vertex))
    }

    override fun <P, V, E> put(
        container: GraphData<ParallelizableEdgeUndirectedGraphWT, P, V, E>,
        line: Line<P>,
        edge: E,
    ): GraphData<ParallelizableEdgeUndirectedGraphWT, P, V, E> {
        return if (includeEdge(container, line, edge) && line is UndirectedLine) {
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
        container: GraphData<ParallelizableEdgeUndirectedGraphWT, P, V, E>,
        point: P,
    ): GraphData<ParallelizableEdgeUndirectedGraphWT, P, V, E> {
        return putAllEdges(
            putAllVertices(empty(), container.narrowed().verticesByPoint.remove(point)),
            streamEdges(container)
        )
    }

    override fun <P, V, E> removeEdges(
        container: GraphData<ParallelizableEdgeUndirectedGraphWT, P, V, E>,
        line: Line<P>,
    ): GraphData<ParallelizableEdgeUndirectedGraphWT, P, V, E> {
        return if (line is UndirectedLine) {
            container
                .narrowed()
                .copy(edgesSetByLine = container.narrowed().edgesSetByLine.remove(line))
        } else {
            container
        }
    }
}
