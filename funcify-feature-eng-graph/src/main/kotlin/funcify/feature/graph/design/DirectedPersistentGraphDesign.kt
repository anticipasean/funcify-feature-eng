package funcify.feature.graph.design

import funcify.feature.graph.DirectedPersistentGraph
import funcify.feature.graph.behavior.GraphBehavior
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

    override val behavior: GraphBehavior<DWT>

    override val data: GraphData<DWT, P, V, E>

    override fun <P, V, E> unit(
        behavior: GraphBehavior<DWT>,
        data: GraphData<DWT, P, V, E>
    ): DirectedPersistentGraphDesign<DWT, P, V, E>

    override fun put(point: P, vertex: V): DirectedPersistentGraph<P, V, E> {
        return unit(behavior, behavior.put(data, point, vertex))
    }

    override fun put(point1: P, point2: P, edge: E): DirectedPersistentGraph<P, V, E> {
        return unit(behavior, behavior.put(data, point1, point2, edge))
    }

    override fun put(line: Line<P>, edge: E): DirectedPersistentGraph<P, V, E> {
        return unit(behavior, behavior.put(data, line, edge))
    }

    override fun <M : Map<P, V>> putAllVertices(vertices: M): DirectedPersistentGraph<P, V, E> {
        return unit(behavior, behavior.putAllVertices(data, vertices))
    }

    override fun <M : Map<Line<P>, E>> putAllEdges(edges: M): DirectedPersistentGraph<P, V, E> {
        return unit(behavior, behavior.putAllEdges(data, edges))
    }

    override fun <S : Set<E>, M : Map<Line<P>, S>> putAllEdgeSets(
        edges: M
    ): DirectedPersistentGraph<P, V, E> {
        return unit(behavior, behavior.putAllEdgeSets(data, edges))
    }

    override fun removeVertex(point: P): DirectedPersistentGraph<P, V, E> {
        return unit(behavior, behavior.removeVertex(data, point))
    }

    override fun removeEdges(point1: P, point2: P): DirectedPersistentGraph<P, V, E> {
        return unit(behavior, behavior.removeEdges(data, point1, point2))
    }

    override fun removeEdges(line: Line<P>): DirectedPersistentGraph<P, V, E> {
        return unit(behavior, behavior.removeEdges(data, line))
    }

    override fun filterVertices(condition: (P, V) -> Boolean): DirectedPersistentGraph<P, V, E> {
        return unit(behavior, behavior.filterVertices(data, condition))
    }

    override fun filterEdges(condition: (Line<P>, E) -> Boolean): DirectedPersistentGraph<P, V, E> {
        return unit(behavior, behavior.filterEdges(data, condition))
    }

    override fun <P1> mapPoints(function: (P, V) -> P1): DirectedPersistentGraph<P1, V, E> {
        return unit(behavior, behavior.mapPoints(data, function))
    }

    override fun <P1> mapPoints(function: (P) -> P1): DirectedPersistentGraph<P1, V, E> {
        return unit(behavior, behavior.mapPoints(data, function))
    }

    override fun <V1> mapVertices(function: (P, V) -> V1): DirectedPersistentGraph<P, V1, E> {
        return unit(behavior, behavior.mapVertices(data, function))
    }

    override fun <E1> mapEdges(function: (Line<P>, E) -> E1): DirectedPersistentGraph<P, V, E1> {
        return unit(behavior, behavior.mapEdges(data, function))
    }

    override fun <P1, V1, M : Map<P1, V1>> flatMapVertices(
        function: (P, V) -> M
    ): DirectedPersistentGraph<P1, V1, E> {
        return unit(behavior, behavior.flatMapVertices(data, function))
    }

    override fun <E1, M : Map<Line<P>, E1>> flatMapEdges(
        function: (Line<P>, E) -> M
    ): DirectedPersistentGraph<P, V, E1> {
        return unit(behavior, behavior.flatMapEdges(data, function))
    }

    override fun hasCycles(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getCycles(): Iterable<Pair<Pair<Line<P>, E>, Pair<Line<P>, E>>> {
        TODO("Not yet implemented")
    }

    override fun getCyclesAsStream(): Stream<out Pair<Triple<P, P, E>, Triple<P, P, E>>> {
        TODO("Not yet implemented")
    }

    override fun successorVertices(point: P): Iterable<Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun successorVerticesAsStream(point: P): Stream<out Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun successorVertices(vertex: V, pointExtractor: (V) -> P): Iterable<Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun successorVerticesAsStream(
        vertex: V,
        pointExtractor: (V) -> P
    ): Stream<out Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun predecessorVertices(point: P): Iterable<Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun predecessorVerticesAsStream(point: P): Stream<out Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun predecessorVertices(vertex: V, pointExtractor: (V) -> P): Iterable<Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun predecessorVerticesAsStream(
        vertex: V,
        pointExtractor: (V) -> P
    ): Stream<out Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun adjacentVertices(point: P): Iterable<Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun adjacentVerticesAsStream(point: P): Stream<out Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun adjacentVertices(vertex: V, pointExtractor: (V) -> P): Iterable<Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun adjacentVerticesAsStream(
        vertex: V,
        pointExtractor: (V) -> P
    ): Stream<out Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun edgesFromPoint(point: P): Iterable<E> {
        TODO("Not yet implemented")
    }

    override fun edgesFromPointAsStream(point: P): Stream<out E> {
        TODO("Not yet implemented")
    }

    override fun edgesToPoint(point: P): Iterable<E> {
        TODO("Not yet implemented")
    }

    override fun edgesToPointAsStream(point: P): Stream<out E> {
        TODO("Not yet implemented")
    }
}
