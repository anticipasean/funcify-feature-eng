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

    fun successorVertices(vertexPath: P): Iterable<Pair<P, V>>

    fun successorVerticesAsStream(vertexPath: P): Stream<out Pair<P, V>>

    fun successorVertices(vertex: @UnsafeVariance V, pathExtractor: (V) -> P): Iterable<Pair<P, V>>

    fun successorVerticesAsStream(
        vertex: @UnsafeVariance V,
        pathExtractor: (V) -> P
    ): Stream<out Pair<P, V>>

    fun predecessorVertices(vertexPath: P): Iterable<Pair<P, V>>

    fun predecessorVerticesAsStream(vertexPath: P): Stream<out Pair<P, V>>

    fun predecessorVertices(
        vertex: @UnsafeVariance V,
        pathExtractor: (V) -> P
    ): Iterable<Pair<P, V>>

    fun predecessorVerticesAsStream(
        vertex: @UnsafeVariance V,
        pathExtractor: (V) -> P
    ): Stream<out Pair<P, V>>

    fun adjacentVertices(vertexPath: P): Iterable<Pair<P, V>>

    fun adjacentVerticesAsStream(vertexPath: P): Stream<out Pair<P, V>>

    fun adjacentVertices(vertex: @UnsafeVariance V, pathExtractor: (V) -> P): Iterable<Pair<P, V>>

    fun adjacentVerticesAsStream(
        vertex: @UnsafeVariance V,
        pathExtractor: (V) -> P
    ): Stream<out Pair<P, V>>

    fun edgesFromPath(path: P): Iterable<E>

    fun edgesFromPathAsStream(path: P): Stream<out E>

    fun edgesToPath(path: P): Iterable<E>

    fun edgesToPathAsStream(path: P): Stream<out E>
}
