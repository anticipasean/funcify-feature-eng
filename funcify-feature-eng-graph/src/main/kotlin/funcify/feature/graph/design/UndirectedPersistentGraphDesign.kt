package funcify.feature.graph.design

import funcify.feature.graph.PersistentGraph
import funcify.feature.graph.behavior.GraphBehavior
import funcify.feature.graph.data.GraphData
import funcify.feature.graph.line.Line

/**
 *
 * @author smccarron
 * @created 2023-01-06
 */
internal interface UndirectedPersistentGraphDesign<DWT, P, V, E> :
    PersistentGraphDesign<DWT, P, V, E> {

    override val behavior: GraphBehavior<DWT>

    override val data: GraphData<DWT, P, V, E>

    override fun put(point: P, vertex: V): PersistentGraph<P, V, E> {
        return unit(behavior.put(data, point, vertex))
    }

    override fun put(point1: P, point2: P, edge: E): PersistentGraph<P, V, E> {
        return unit(behavior.put(data, point1, point2, edge))
    }

    override fun put(line: Line<P>, edge: E): PersistentGraph<P, V, E> {
        return unit(behavior.put(data, line, edge))
    }

    override fun <M : Map<P, V>> putAllVertices(vertices: M): PersistentGraph<P, V, E> {
        return unit(behavior.putAllVertices(data, vertices))
    }

    override fun <M : Map<Line<P>, E>> putAllEdges(edges: M): PersistentGraph<P, V, E> {
        return unit(behavior.putAllEdges(data, edges))
    }

    override fun <S : Set<E>, M : Map<Line<P>, S>> putAllEdgeSets(
        edges: M
    ): PersistentGraph<P, V, E> {
        return unit(behavior.putAllEdgeSets(data, edges))
    }

    override fun removeVertex(point: P): PersistentGraph<P, V, E> {
        return unit(behavior.removeVertex(data, point))
    }

    override fun removeEdges(point1: P, point2: P): PersistentGraph<P, V, E> {
        return unit(behavior.removeEdges(data, point1, point2))
    }

    override fun removeEdges(line: Line<P>): PersistentGraph<P, V, E> {
        return unit(behavior.removeEdges(data, line))
    }

    override fun filterVertices(condition: (P, V) -> Boolean): PersistentGraph<P, V, E> {
        return unit(behavior.filterVertices(data, condition))
    }

    override fun filterEdges(condition: (Line<P>, E) -> Boolean): PersistentGraph<P, V, E> {
        return unit(behavior.filterEdges(data, condition))
    }

    override fun <P1> mapPoints(function: (P, V) -> P1): PersistentGraph<P1, V, E> {
        return unit(behavior.mapPoints(data, function))
    }

    override fun <P1> mapPoints(function: (P) -> P1): PersistentGraph<P1, V, E> {
        return unit(behavior.mapPoints(data, function))
    }

    override fun <V1> mapVertices(function: (P, V) -> V1): PersistentGraph<P, V1, E> {
        return unit(behavior.mapVertices(data, function))
    }

    override fun <E1> mapEdges(function: (Line<P>, E) -> E1): PersistentGraph<P, V, E1> {
        return unit(behavior.mapEdges(data, function))
    }

    override fun <P1, V1, M : Map<P1, V1>> flatMapVertices(
        function: (P, V) -> M
    ): PersistentGraph<P1, V1, E> {
        return unit(behavior.flatMapVertices(data, function))
    }

    override fun <E1, M : Map<Line<P>, E1>> flatMapEdges(
        function: (Line<P>, E) -> M
    ): PersistentGraph<P, V, E1> {
        return unit(behavior.flatMapEdges(data, function))
    }

    override fun <V1, M : Map<P, V1>, V2> zipVertices(
        other: M,
        function: (V, V1) -> V2
    ): PersistentGraph<P, V2, E> {
        return unit(behavior.zipVertices(data, other, function))
    }

    override fun <E1, M : Map<Line<P>, E1>, E2> zipEdges(
        other: M,
        function: (E, E1) -> E2
    ): PersistentGraph<P, V, E2> {
        return unit(behavior.zipEdges(data, other, function))
    }
}
