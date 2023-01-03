package funcify.feature.graph

import java.util.stream.Stream

/**
 *
 * @author smccarron
 * @created 2023-01-01
 */
interface DirectedPersistentGraph<P, V, E> : PersistentGraph<P, V, E> {

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
