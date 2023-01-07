package funcify.feature.graph

import funcify.feature.graph.line.Line
import java.util.*
import java.util.stream.Stream
import kotlinx.collections.immutable.ImmutableSet

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
     * Point <P> uniquely identifies and acts as a label for a vertex in the graph and may even be
     * the same value as its vertex if desired
     *
     * - ImmutableGraph<Int, Char, Double>: e.g. ( Path: 1, Vertex: 'A' ), ( Path: 2, Vertex: 'B' ),
     * ( PathPair: ( 1, 2 ), Edge: 0.3 )
     */
    operator fun get(point: P): V?

    /**
     * Point <P> and another Point <P> pair uniquely identify a Line<P> with an edge (or edges if
     * parallel edges are permitted) in the graph
     *
     * - ImmutableGraph<Char, Int, Double>: e.g. ( Path: 'A', Vertex: 1 ), ( Path: 'B', Vertex: 2 ),
     * ( Line: ('A','B'), Edge: 0.2 )
     */
    operator fun get(point1: P, point2: P): Iterable<E>

    /**
     * Line<P> uniquely identifies an edge (or edges if parallel edges are permitted) in the graph
     *
     * - ImmutableGraph<Char, Int, Double>: e.g. ( Path: 'A', Vertex: 1 ), ( Path: 'B', Vertex: 2 ),
     * ( Line: ('A','B'), Edge: 0.1 )
     */
    operator fun get(line: Line<P>): Iterable<E>

    /** Alias method for [ImmutableGraph.get] */
    fun getVertex(point: P): V? {
        return this[point]
    }

    fun getVertexOrDefault(point: P, defaultValue: @UnsafeVariance V): V {
        return this[point] ?: defaultValue
    }

    /** Alias method for [ImmutableGraph.get] */
    fun getEdge(point1: P, point2: P): Iterable<E> {
        return this[point1, point2]
    }

    /** Alias method for [ImmutableGraph.get] */
    fun getEdge(line: Line<P>): Iterable<E> {
        return this[line]
    }

    fun descriptors(): ImmutableSet<GraphDescriptor>

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
    fun lines(): Iterable<Line<P>>

    /** Fetch all path pairs for which this is at least one edge */
    fun linesAsStream(): Stream<out Line<P>>

    /**
     * Filter out all vertices that do not meet the given condition (and any edges that are thereby
     * excluded since they would no longer refer to an existing path to a vertex)
     */
    fun filterVertices(condition: (P, V) -> Boolean): ImmutableGraph<P, V, E>

    /**
     * Filter out all vertices that do not meet the given condition (and any edges that are thereby
     * excluded since they would no longer refer to an existing path to a vertex) without the
     * context of point <P>
     */
    fun filterVertices(condition: (V) -> Boolean): ImmutableGraph<P, V, E>

    /** Filter out all edges that do not meet the given condition with the context of path <P> */
    fun filterEdges(condition: (Line<P>, E) -> Boolean): ImmutableGraph<P, V, E>

    /** Filter out all edges that do not meet the given condition without the context of path <P> */
    fun filterEdges(condition: (E) -> Boolean): ImmutableGraph<P, V, E>

    /** Transform all points <P> to <P1> with the context of vertex <V> */
    fun <P1> mapPoints(function: (P, V) -> P1): ImmutableGraph<P1, V, E>

    /** Transform all points <P> to <P1> without the context of vertex <V> */
    fun <P1> mapPoints(function: (P) -> P1): ImmutableGraph<P1, V, E>

    /** Transform all vertices <V> to <V1> with the context of point <P> */
    fun <V1> mapVertices(function: (P, V) -> V1): ImmutableGraph<P, V1, E>

    /** Transform all vertices <V> to <V1> without the context of point <P> */
    fun <V1> mapVertices(function: (V) -> V1): ImmutableGraph<P, V1, E>

    /** Transform all edges from <E> to <R> with the context of path <P> */
    fun <E1> mapEdges(function: (Line<P>, E) -> E1): ImmutableGraph<P, V, E1>

    /** Transform all edges from <E> to <R> without the context of path <P> */
    fun <E1> mapEdges(function: (E) -> E1): ImmutableGraph<P, V, E1>

    /**
     * Transform each path-to-vertex entry into map of new path-to-vertex entries ( and remove any
     * existing edges that no longer refer to an existing path to a vertex )
     */
    fun <P1, V1, M : Map<P1, @UnsafeVariance V1>> flatMapVertices(
        function: (P, V) -> M
    ): ImmutableGraph<P1, V1, E>

    /**
     * Transform each path-pair-to-edge entry into map of new path-pair-to-edge entries ( discarding
     * any path-pair-to-edge entries wherein either path within the path-pair no longer refers /
     * does not refer to an existing path for a vertex )
     */
    fun <E1, M : Map<Line<P>, @UnsafeVariance E1>> flatMapEdges(
        function: (Line<P>, E) -> M
    ): ImmutableGraph<P, V, E1>

    fun <T> foldLeftVertices(initial: T, accumulator: (T, Pair<P, V>) -> T): T

    fun <T> foldLeftEdges(initial: T, accumulator: (T, Pair<Line<P>, E>) -> T): T

    fun <T> foldRightVertices(initial: T, accumulator: (Pair<P, V>, T) -> T): T

    fun <T> foldRightEdges(initial: T, accumulator: (Pair<Line<P>, E>, T) -> T): T

    /** Method used to generate the #toString representation of the underlying graph */
    fun stringify(
        pointStringifier: (P) -> String = Objects::toString,
        vertexStringifier: (V) -> String = Objects::toString,
        edgeStringifier: (E) -> String = Objects::toString
    ): String

    fun <V1, M : Map<P, V1>, V2> zipVertices(
        other: M,
        function: (V, V1) -> V2
    ): ImmutableGraph<P, V2, E>

    fun <E1, M : Map<Line<P>, E1>, E2> zipEdges(
        other: M,
        function: (E, E1) -> E2
    ): ImmutableGraph<P, V, E2>
}
