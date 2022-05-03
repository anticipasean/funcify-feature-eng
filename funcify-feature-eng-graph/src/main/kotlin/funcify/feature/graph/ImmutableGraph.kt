package funcify.feature.graph

import arrow.core.Tuple5
import java.util.stream.Stream

interface ImmutableGraph<P, out V, out E> {

    operator fun get(path: P): V?

    operator fun get(path1: P, path2: P): Iterable<E>

    operator fun get(pathPair: Pair<P, P>): Iterable<E>

    fun getVertex(path: P): V? {
        return this[path]
    }

    fun getVertexOrDefault(path: P, defaultValue: @UnsafeVariance V): V {
        return this[path] ?: defaultValue
    }

    fun getEdge(path1: P, path2: P): Iterable<E> {
        return this[path1, path2]
    }

    fun getEdge(pathPair: Pair<P, P>): Iterable<E> {
        return this[pathPair]
    }

    fun vertexCount(): Int

    fun edgeCount(): Int

    fun vertices(): Iterable<V>

    fun verticesAsStream(): Stream<out V>

    fun edges(): Iterable<E>

    fun edgesAsStream(): Stream<out E>

    fun connectedPaths(): Iterable<Pair<P, P>>

    fun connectedPathsAsStream(): Stream<out Pair<P, P>>

    fun filterVertices(condition: (V) -> Boolean): ImmutableGraph<P, V, E>

    fun filterEdges(condition: (E) -> Boolean): ImmutableGraph<P, V, E>

    fun <R> mapVertices(function: (V) -> R): ImmutableGraph<P, R, E>

    fun <R> mapEdges(function: (E) -> R): ImmutableGraph<P, V, R>

    fun <R, M : Map<out P, @UnsafeVariance R>> flatMapVertices(
        function: (P, V) -> M
    ): ImmutableGraph<P, R, E>

    fun <R, M : Map<out Pair<P, P>, @UnsafeVariance R>> flatMapEdges(
        function: (Pair<P, P>, E) -> M
    ): ImmutableGraph<P, V, R>

    fun hasCycles(): Boolean

    fun getCycles(): Iterable<Pair<Triple<P, P, E>, Triple<P, P, E>>>

    fun getCyclesAsStream(): Stream<out Pair<Triple<P, P, E>, Triple<P, P, E>>>

    fun depthFirstSearchOnPath(path: P): Stream<out Tuple5<V, P, E, P, V>>

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
