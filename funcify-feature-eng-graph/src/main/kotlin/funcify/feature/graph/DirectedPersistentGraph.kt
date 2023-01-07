package funcify.feature.graph

import funcify.feature.graph.line.Line
import java.util.stream.Stream

/**
 *
 * @author smccarron
 * @created 2023-01-01
 */
interface DirectedPersistentGraph<P, V, E> : PersistentGraph<P, V, E> {

    override fun put(point: P, vertex: V): DirectedPersistentGraph<P, V, E>

    override fun put(point1: P, point2: P, edge: E): DirectedPersistentGraph<P, V, E>

    override fun put(line: Line<P>, edge: E): DirectedPersistentGraph<P, V, E>

    override fun <M : Map<P, V>> putAllVertices(vertices: M): DirectedPersistentGraph<P, V, E>

    override fun <M : Map<Line<P>, E>> putAllEdges(edges: M): DirectedPersistentGraph<P, V, E>

    override fun <S : Set<E>, M : Map<Line<P>, S>> putAllEdgeSets(
        edges: M
    ): DirectedPersistentGraph<P, V, E>

    override fun removeVertex(point: P): DirectedPersistentGraph<P, V, E>

    override fun removeEdges(point1: P, point2: P): DirectedPersistentGraph<P, V, E>

    override fun removeEdges(line: Line<P>): DirectedPersistentGraph<P, V, E>

    override fun filterVertices(condition: (P, V) -> Boolean): DirectedPersistentGraph<P, V, E>

    override fun filterVertices(condition: (V) -> Boolean): DirectedPersistentGraph<P, V, E> {
        return filterVertices { _: P, v: V -> condition(v) }
    }

    override fun filterEdges(condition: (Line<P>, E) -> Boolean): DirectedPersistentGraph<P, V, E>

    override fun filterEdges(condition: (E) -> Boolean): DirectedPersistentGraph<P, V, E> {
        return filterEdges { _: Line<P>, e: E -> condition(e) }
    }

    override fun <P1> mapPoints(function: (P, V) -> P1): DirectedPersistentGraph<P1, V, E>

    override fun <P1> mapPoints(function: (P) -> P1): DirectedPersistentGraph<P1, V, E> {
        return mapPoints { p: P, _: V -> function(p) }
    }

    override fun <V1> mapVertices(function: (P, V) -> V1): DirectedPersistentGraph<P, V1, E>

    override fun <V1> mapVertices(function: (V) -> V1): DirectedPersistentGraph<P, V1, E> {
        return mapVertices { _: P, v: V -> function(v) }
    }

    override fun <E1> mapEdges(function: (Line<P>, E) -> E1): DirectedPersistentGraph<P, V, E1>

    override fun <E1> mapEdges(function: (E) -> E1): DirectedPersistentGraph<P, V, E1> {
        return mapEdges { _: Line<P>, e: E -> function(e) }
    }

    override fun <P1, V1, M : Map<P1, V1>> flatMapVertices(
        function: (P, V) -> M
    ): DirectedPersistentGraph<P1, V1, E>

    override fun <E1, M : Map<Line<P>, E1>> flatMapEdges(
        function: (Line<P>, E) -> M
    ): DirectedPersistentGraph<P, V, E1>

    /** Directed-Specific Methods */

    fun successorVertices(point: P): Iterable<Pair<P, V>>

    fun successorVerticesAsStream(point: P): Stream<out Pair<P, V>>

    fun successorVertices(vertex: @UnsafeVariance V, pointExtractor: (V) -> P): Iterable<Pair<P, V>>

    fun successorVerticesAsStream(
        vertex: @UnsafeVariance V,
        pointExtractor: (V) -> P
    ): Stream<out Pair<P, V>>

    fun predecessorVertices(point: P): Iterable<Pair<P, V>>

    fun predecessorVerticesAsStream(point: P): Stream<out Pair<P, V>>

    fun predecessorVertices(
        vertex: @UnsafeVariance V,
        pointExtractor: (V) -> P
    ): Iterable<Pair<P, V>>

    fun predecessorVerticesAsStream(
        vertex: @UnsafeVariance V,
        pointExtractor: (V) -> P
    ): Stream<out Pair<P, V>>

    fun adjacentVertices(point: P): Iterable<Pair<P, V>>

    fun adjacentVerticesAsStream(point: P): Stream<out Pair<P, V>>

    fun adjacentVertices(vertex: @UnsafeVariance V, pointExtractor: (V) -> P): Iterable<Pair<P, V>>

    fun adjacentVerticesAsStream(
        vertex: @UnsafeVariance V,
        pointExtractor: (V) -> P
    ): Stream<out Pair<P, V>>

    fun edgesFromPoint(point: P): Iterable<Pair<Line<P>, E>>

    fun edgesFromPointAsStream(point: P): Stream<out Pair<Line<P>, E>>

    fun edgesToPoint(point: P): Iterable<Pair<Line<P>, E>>

    fun edgesToPointAsStream(point: P): Stream<out Pair<Line<P>, E>>
}
