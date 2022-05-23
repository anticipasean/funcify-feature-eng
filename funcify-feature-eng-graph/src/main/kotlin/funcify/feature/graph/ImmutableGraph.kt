package funcify.feature.graph

import arrow.core.Tuple5
import java.util.stream.Stream

interface ImmutableGraph<P, out V, out E> {

    /**
     * Path of type <P> uniquely identifies a vertex in the graph and may even be the same value as
     * its vertex if desired
     * - ImmutableGraph<Int, Int, Char>: e.g. ( Path: 1, Vertex: 1 ), ( Path: 2, Vertex: 2 ), (
     * PathPair: ( 1, 2 ), Edge: 'A' )
     */
    operator fun get(path: P): V?

    /**
     * Path of type <P> and another Path of type <P> as a pair uniquely identifies an edge in the
     * graph and may even be the same type as its paths if desired
     * - ImmutableGraph<Char, Int, Char>: e.g. ( Path: 'A', Vertex: 1 ), ( Path: 'B', Vertex: 2 ), (
     * PathPair: ( 'A', 'B' ), Edge: 'C' )
     */
    operator fun get(path1: P, path2: P): Iterable<E>

    /**
     * Path of type <P> and another Path of type <P> as a pair uniquely identifies an edge in the
     * graph and may even be the same type as its paths if desired
     * - ImmutableGraph<Char, Int, Char>: e.g. ( Path: 'A', Vertex: 1 ), ( Path: 'B', Vertex: 2 ), (
     * PathPair: ( 'A', 'B' ), Edge: 'C' )
     */
    operator fun get(pathPair: Pair<P, P>): Iterable<E>

    /** Alias method for [ImmutableGraph.get] */
    fun getVertex(path: P): V? {
        return this[path]
    }

    fun getVertexOrDefault(path: P, defaultValue: @UnsafeVariance V): V {
        return this[path] ?: defaultValue
    }

    /** Alias method for [ImmutableGraph.get] */
    fun getEdge(path1: P, path2: P): Iterable<E> {
        return this[path1, path2]
    }

    /** Alias method for [ImmutableGraph.get] */
    fun getEdge(pathPair: Pair<P, P>): Iterable<E> {
        return this[pathPair]
    }

    /** Terminal Op: Eagerly determine the number of vertices */
    fun vertexCount(): Int

    /** Terminal Op: Eagerly determine the number of edges */
    fun edgeCount(): Int

    /** Terminal Op: Eagerly fetch all vertices */
    fun vertices(): Iterable<V>

    /** Terminal Op: Eagerly fetch all vertices */
    fun verticesAsStream(): Stream<out V>

    /** Terminal Op: Eagerly fetch all edges */
    fun edges(): Iterable<E>

    /** Terminal Op: Eagerly fetch all edges */
    fun edgesAsStream(): Stream<out E>

    /** Terminal Op: Eagerly fetch all path pairs for which this is at least one edge */
    fun connectedPaths(): Iterable<Pair<P, P>>

    /** Terminal Op: Eagerly fetch all path pairs for which this is at least one edge */
    fun connectedPathsAsStream(): Stream<out Pair<P, P>>

    /**
     * Intermediate Op: Lazily filter out all vertices that do not meet the given condition (and any
     * edges that are thereby excluded since they would no longer refer to an existing path to a
     * vertex)
     */
    fun filterVertices(condition: (V) -> Boolean): ImmutableGraph<P, V, E>

    /** Intermediate Op: Lazily filter out all edges that do not meet the given condition */
    fun filterEdges(condition: (E) -> Boolean): ImmutableGraph<P, V, E>

    /** Intermediate Op: Lazily transform all vertices */
    fun <R> mapVertices(function: (V) -> R): ImmutableGraph<P, R, E>

    /** Intermediate Op: Lazily transform all edges */
    fun <R> mapEdges(function: (E) -> R): ImmutableGraph<P, V, R>

    /**
     * Intermediate Op: Lazily transform each path-to-vertex entry into map of new path-to-vertex
     * entries ( and remove any existing edges that no longer refer to an existing path to a vertex
     * )
     */
    fun <R, M : Map<out P, @UnsafeVariance R>> flatMapVertices(
        function: (P, V) -> M
    ): ImmutableGraph<P, R, E>

    /**
     * Intermediate Op: Lazily transform each path-pair-to-edge entry into map of new
     * path-pair-to-edge entries ( discarding any path-pair-to-edge entries wherein either path
     * within the path-pair no longer refers / does not refer to an existing path for a vertex )
     */
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
