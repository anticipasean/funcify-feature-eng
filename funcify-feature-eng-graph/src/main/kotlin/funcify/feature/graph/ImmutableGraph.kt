package funcify.feature.graph

import java.util.stream.Stream

/**
 * @param P
 * - Path or Point type parameter
 * @param V
 * - Vertex type parameter
 * @param E
 * - Edge type parameter
 */
interface ImmutableGraph<P, out V, out E> {

    /**
     * Path/Point of type <P> uniquely identifies and acts as a label for a vertex in the graph and
     * may even be the same value as its vertex if desired
     * - ImmutableGraph<Int, Char, Double>: e.g. ( Path: 1, Vertex: 'A' ), ( Path: 2, Vertex: 'B' ),
     * ( PathPair: ( 1, 2 ), Edge: 0.3 )
     */
    operator fun get(path: P): V?

    /**
     * Path/Point of type <P> and another Path/Point of type <P> as a pair uniquely identifies an
     * edge in the graph and may even be the same type as its paths if desired
     * - ImmutableGraph<Char, Int, Double>: e.g. ( Path: 'A', Vertex: 1 ), ( Path: 'B', Vertex: 2 ),
     * ( PathPair: ( 'A', 'B' ), Edge: 0.2 )
     */
    operator fun get(path1: P, path2: P): Iterable<E>

    /**
     * Path/Point of type <P> and another Path/Point of type <P> as a pair uniquely identifies an
     * edge in the graph and may even be the same type as its paths if desired
     * - ImmutableGraph<Char, Int, Double>: e.g. ( Path: 'A', Vertex: 1 ), ( Path: 'B', Vertex: 2 ),
     * ( PathPair: ( 'A', 'B' ), Edge: 0.1 )
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

    /** Determine the number of vertices */
    fun vertexCount(): Int

    /** Determine the number of edges */
    fun edgeCount(): Int

    /** Fetch all vertices */
    fun vertices(): Iterable<V>

    /** Fetch all vertices */
    fun verticesAsStream(): Stream<out V>

    /** Fetch all edges */
    fun edges(): Iterable<E>

    /** Fetch all edges */
    fun edgesAsStream(): Stream<out E>

    /** Fetch all path pairs for which this is at least one edge */
    fun connectedPaths(): Iterable<Pair<P, P>>

    /** Fetch all path pairs for which this is at least one edge */
    fun connectedPathsAsStream(): Stream<out Pair<P, P>>

    /**
     * Filter out all vertices that do not meet the given condition (and any edges that are thereby
     * excluded since they would no longer refer to an existing path to a vertex)
     */
    fun filterVertices(condition: (P, V) -> Boolean): ImmutableGraph<P, V, E>

    /** Filter out all edges that do not meet the given condition */
    fun filterEdges(condition: (Pair<P, P>, E) -> Boolean): ImmutableGraph<P, V, E>

    /** Transform all vertices <V> to <R> */
    fun <R> mapVertices(function: (P, V) -> R): ImmutableGraph<P, R, E>

    /** Transform all edges from <E> to <R> */
    fun <R> mapEdges(function: (Pair<P, P>, E) -> R): ImmutableGraph<P, V, R>

    /**
     * Transform each path-to-vertex entry into map of new path-to-vertex entries ( and remove any
     * existing edges that no longer refer to an existing path to a vertex )
     */
    fun <R, M : Map<out P, @UnsafeVariance R>> flatMapVertices(
        function: (P, V) -> M
    ): ImmutableGraph<P, R, E>

    /**
     * Transform each path-pair-to-edge entry into map of new path-pair-to-edge entries ( discarding
     * any path-pair-to-edge entries wherein either path within the path-pair no longer refers /
     * does not refer to an existing path for a vertex )
     */
    fun <R, M : Map<out Pair<P, P>, @UnsafeVariance R>> flatMapEdges(
        function: (Pair<P, P>, E) -> M
    ): ImmutableGraph<P, V, R>

    fun <R> foldLeftVertices(initial: R, accumulator: (R, Pair<P, V>) -> R): R

    fun <R> foldLeftEdges(initial: R, accumulator: (R, Pair<Pair<P, P>, E>) -> R): R

    fun <R> foldRightVertices(initial: R, accumulator: (Pair<P, V>, R) -> R): R

    fun <R> foldRightEdges(initial: R, accumulator: (Pair<Pair<P, P>, E>, R) -> R): R
}
