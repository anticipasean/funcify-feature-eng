package funcify.feature.graph.design

import funcify.feature.graph.DirectedPersistentGraph
import funcify.feature.graph.behavior.DirectedGraphBehavior
import funcify.feature.graph.data.GraphData
import funcify.feature.graph.line.Line
import java.util.stream.Stream

/**
 *
 * @author smccarron
 * @created 2023-01-01
 */
internal interface DirectedPersistentGraphDesign<DWT, P, V, E> :
    PersistentGraphDesign<DWT, P, V, E>, DirectedPersistentGraph<P, V, E> {

    override val behavior: DirectedGraphBehavior<DWT>

    override val data: GraphData<DWT, P, V, E>

    override fun <P, V, E> unit(
        data: GraphData<DWT, P, V, E>
    ): DirectedPersistentGraphDesign<DWT, P, V, E>

    override fun put(point: P, vertex: V): DirectedPersistentGraph<P, V, E> {
        return unit(behavior.put(data, point, vertex))
    }

    override fun put(point1: P, point2: P, edge: E): DirectedPersistentGraph<P, V, E> {
        return unit(behavior.put(data, point1, point2, edge))
    }

    override fun put(line: Line<P>, edge: E): DirectedPersistentGraph<P, V, E> {
        return unit(behavior.put(data, line, edge))
    }

    override fun <M : Map<P, V>> putAllVertices(vertices: M): DirectedPersistentGraph<P, V, E> {
        return unit(behavior.putAllVertices(data, vertices))
    }

    override fun <M : Map<Line<P>, E>> putAllEdges(edges: M): DirectedPersistentGraph<P, V, E> {
        return unit(behavior.putAllEdges(data, edges))
    }

    override fun <S : Set<E>, M : Map<Line<P>, S>> putAllEdgeSets(
        edges: M
    ): DirectedPersistentGraph<P, V, E> {
        return unit(behavior.putAllEdgeSets(data, edges))
    }

    override fun removeVertex(point: P): DirectedPersistentGraph<P, V, E> {
        return unit(behavior.removeVertex(data, point))
    }

    override fun removeEdges(point1: P, point2: P): DirectedPersistentGraph<P, V, E> {
        return unit(behavior.removeEdges(data, point1, point2))
    }

    override fun removeEdges(line: Line<P>): DirectedPersistentGraph<P, V, E> {
        return unit(behavior.removeEdges(data, line))
    }

    override fun filterVertices(condition: (P, V) -> Boolean): DirectedPersistentGraph<P, V, E> {
        return unit(behavior.filterVertices(data, condition))
    }

    override fun filterEdges(condition: (Line<P>, E) -> Boolean): DirectedPersistentGraph<P, V, E> {
        return unit(behavior.filterEdges(data, condition))
    }

    override fun <P1> mapPoints(function: (P, V) -> P1): DirectedPersistentGraph<P1, V, E> {
        return unit(behavior.mapPoints(data, function))
    }

    override fun <P1> mapPoints(function: (P) -> P1): DirectedPersistentGraph<P1, V, E> {
        return unit(behavior.mapPoints(data, function))
    }

    override fun <V1> mapVertices(function: (P, V) -> V1): DirectedPersistentGraph<P, V1, E> {
        return unit(behavior.mapVertices(data, function))
    }

    override fun <E1> mapEdges(function: (Line<P>, E) -> E1): DirectedPersistentGraph<P, V, E1> {
        return unit(behavior.mapEdges(data, function))
    }

    override fun <P1, V1, M : Map<P1, V1>> flatMapVertices(
        function: (P, V) -> M
    ): DirectedPersistentGraph<P1, V1, E> {
        return unit(behavior.flatMapVertices(data, function))
    }

    override fun <E1, M : Map<Line<P>, E1>> flatMapEdges(
        function: (Line<P>, E) -> M
    ): DirectedPersistentGraph<P, V, E1> {
        return unit(behavior.flatMapEdges(data, function))
    }

    override fun <V1, M : Map<P, V1>, V2> zipVertices(
        other: M,
        function: (V, V1) -> V2
    ): DirectedPersistentGraph<P, V2, E> {
        return unit(behavior.zipVertices(data, other, function))
    }

    override fun <E1, M : Map<Line<P>, E1>, E2> zipEdges(
        other: M,
        function: (E, E1) -> E2
    ): DirectedPersistentGraph<P, V, E2> {
        return unit(behavior.zipEdges(data, other, function))
    }

    override fun successorVertices(point: P): Iterable<Pair<P, V>> {
        return Iterable { successorVerticesAsStream(point).iterator() }
    }

    override fun successorVerticesAsStream(point: P): Stream<out Pair<P, V>> {
        return behavior.successorVerticesAsStream(data, point)
    }

    override fun successorVertices(vertex: V, pointExtractor: (V) -> P): Iterable<Pair<P, V>> {
        return successorVertices(pointExtractor(vertex))
    }

    override fun successorVerticesAsStream(
        vertex: V,
        pointExtractor: (V) -> P
    ): Stream<out Pair<P, V>> {
        return successorVerticesAsStream(pointExtractor(vertex))
    }

    override fun predecessorVerticesAsStream(point: P): Stream<out Pair<P, V>> {
        return behavior.predecessorVerticesAsStream(data, point)
    }

    override fun predecessorVertices(point: P): Iterable<Pair<P, V>> {
        return Iterable { predecessorVerticesAsStream(point).iterator() }
    }

    override fun predecessorVertices(vertex: V, pointExtractor: (V) -> P): Iterable<Pair<P, V>> {
        return predecessorVertices(pointExtractor(vertex))
    }

    override fun predecessorVerticesAsStream(
        vertex: V,
        pointExtractor: (V) -> P
    ): Stream<out Pair<P, V>> {
        return predecessorVerticesAsStream(pointExtractor(vertex))
    }

    override fun adjacentVertices(point: P): Iterable<Pair<P, V>> {
        return Iterable { adjacentVerticesAsStream(point).iterator() }
    }

    override fun adjacentVerticesAsStream(point: P): Stream<out Pair<P, V>> {
        return Stream.concat(predecessorVerticesAsStream(point), successorVerticesAsStream(point))
    }

    override fun adjacentVertices(vertex: V, pointExtractor: (V) -> P): Iterable<Pair<P, V>> {
        return adjacentVertices(pointExtractor(vertex))
    }

    override fun adjacentVerticesAsStream(
        vertex: V,
        pointExtractor: (V) -> P
    ): Stream<out Pair<P, V>> {
        return adjacentVerticesAsStream(pointExtractor(vertex))
    }

    override fun edgesFromPoint(point: P): Iterable<Pair<Line<P>, E>> {
        return Iterable { edgesFromPointAsStream(point).iterator() }
    }

    override fun edgesFromPointAsStream(point: P): Stream<out Pair<Line<P>, E>> {
        return behavior.edgesFromPointAsStream(data, point)
    }

    override fun edgesToPoint(point: P): Iterable<Pair<Line<P>, E>> {
        return Iterable { edgesToPointAsStream(point).iterator() }
    }

    override fun edgesToPointAsStream(point: P): Stream<out Pair<Line<P>, E>> {
        return behavior.edgesToPointAsStream(data, point)
    }
}
