package funcify.feature.graph

import java.util.stream.Stream

/**
 *
 * @author smccarron
 * @created 2023-01-01
 */
interface DirectedPersistentGraph<P, V, E> : PersistentGraph<P, V, E> {

    override fun put(point: P, vertex: V): DirectedPersistentGraph<P, V, E>

    override fun put(point1: P, point2: P, edge: E): DirectedPersistentGraph<P, V, E>

    override fun put(pointPair: Pair<P, P>, edge: E): DirectedPersistentGraph<P, V, E>

    override fun <M : Map<P, V>> putAllVertices(vertices: M): DirectedPersistentGraph<P, V, E>

    override fun <M : Map<Pair<P, P>, E>> putAllEdges(edges: M): DirectedPersistentGraph<P, V, E>

    override fun <S : Set<E>, M : Map<Pair<P, P>, S>> putAllEdgeSets(
        edges: M
    ): DirectedPersistentGraph<P, V, E>

    override fun remove(point: P): DirectedPersistentGraph<P, V, E>

    override fun filterVertices(condition: (P, V) -> Boolean): DirectedPersistentGraph<P, V, E>

    override fun filterVertices(condition: (V) -> Boolean): DirectedPersistentGraph<P, V, E> {
        return filterVertices { _: P, v: V -> condition(v) }
    }

    override fun filterEdges(
        condition: (Pair<P, P>, E) -> Boolean
    ): DirectedPersistentGraph<P, V, E>

    override fun filterEdges(condition: (E) -> Boolean): DirectedPersistentGraph<P, V, E> {
        return filterEdges { _: Pair<P, P>, e: E -> condition(e) }
    }

    override fun <R> mapPoints(function: (P, V) -> R): DirectedPersistentGraph<R, V, E>

    override fun <R> mapPoints(function: (P) -> R): DirectedPersistentGraph<R, V, E> {
        return mapPoints { p: P, _: V -> function(p) }
    }

    override fun <R> mapVertices(function: (P, V) -> R): DirectedPersistentGraph<P, R, E>

    override fun <R> mapVertices(function: (V) -> R): DirectedPersistentGraph<P, R, E> {
        return mapVertices { _: P, v: V -> function(v) }
    }

    override fun <R> mapEdges(function: (Pair<P, P>, E) -> R): DirectedPersistentGraph<P, V, R>

    override fun <R> mapEdges(function: (E) -> R): DirectedPersistentGraph<P, V, R> {
        return mapEdges { _: Pair<P, P>, e: E -> function(e) }
    }

    override fun <P1, V1, M : Map<out P1, V1>> flatMapVertices(
        function: (P, V) -> M
    ): DirectedPersistentGraph<P1, V1, E>

    override fun <E1, M : Map<out Pair<P, P>, E1>> flatMapEdges(
        function: (Pair<P, P>, E) -> M
    ): DirectedPersistentGraph<P, V, E1>

    /** Directed-Specific Methods */
    fun hasCycles(): Boolean

    fun getCycles(): Iterable<Pair<Triple<P, P, E>, Triple<P, P, E>>>

    fun getCyclesAsStream(): Stream<out Pair<Triple<P, P, E>, Triple<P, P, E>>>

    //    fun depthFirstSearchOnPath(path: P): Stream<out Tuple5<V, P, E, P, V>>

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

    fun edgesFromPoint(point: P): Iterable<E>

    fun edgesFromPointAsStream(point: P): Stream<out E>

    fun edgesToPoint(point: P): Iterable<E>

    fun edgesToPointAsStream(point: P): Stream<out E>
}
