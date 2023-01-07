package funcify.feature.graph.behavior

import funcify.feature.graph.data.GraphData
import funcify.feature.graph.line.Line
import java.util.stream.Stream
import java.util.stream.StreamSupport
import kotlinx.collections.immutable.PersistentSet

/**
 * All behaviors must be stateless
 * @param DWT
 * - **Data** **Witness** **Type** parameter
 */
internal interface GraphBehavior<DWT> {

    /** Monoid Methods */
    fun <P, V, E> empty(): GraphData<DWT, P, V, E>

    fun <P, V, E> combine(
        container1: GraphData<DWT, P, V, E>,
        container2: GraphData<DWT, P, V, E>
    ): GraphData<DWT, P, V, E> {
        return when {
            /** If there aren't any vertices, there shouldn't be any edges */
            container1 === empty<P, V, E>() || vertexCount(container1) == 0 -> {
                container2
            }
            /** If there aren't any vertices, there shouldn't be any edges */
            container2 === empty<P, V, E>() || vertexCount(container2) == 0 -> {
                container1
            }
            else -> {
                putAllEdges(
                    putAllVertices(container1, streamVertices(container2)),
                    streamEdges(container2)
                )
            }
        }
    }

    /** Should create the appropriate line type for the given graph subtype */
    fun <P> line(firstOrSource: P, secondOrDestination: P): Line<P>

    fun <P, V, E> verticesByPoint(container: GraphData<DWT, P, V, E>): Map<P, V>

    fun <P, V, E> vertexCount(container: GraphData<DWT, P, V, E>): Int {
        return verticesByPoint(container).size
    }

    /** Stream Methods */
    fun <P, V, E> vertices(container: GraphData<DWT, P, V, E>): Iterable<Pair<P, V>> {
        return Iterable { streamVertices(container).iterator() }
    }

    fun <P, V, E> streamVertices(container: GraphData<DWT, P, V, E>): Stream<out Pair<P, V>> {
        return verticesByPoint(container).entries.stream().map { (p: P, v: V) -> p to v }
    }

    fun <P, V, E> edges(container: GraphData<DWT, P, V, E>): Iterable<Pair<Line<P>, E>> {
        return Iterable { streamEdges(container).iterator() }
    }

    fun <P, V, E> streamEdges(container: GraphData<DWT, P, V, E>): Stream<out Pair<Line<P>, E>>

    /** Basic Map Methods */
    fun <P, V, E> get(container: GraphData<DWT, P, V, E>, point: P): V? {
        return verticesByPoint(container)[point]
    }

    fun <P, V, E> get(container: GraphData<DWT, P, V, E>, point1: P, point2: P): Iterable<E> {
        return get(container, line(point1, point2))
    }

    fun <P, V, E> get(container: GraphData<DWT, P, V, E>, line: Line<P>): Iterable<E>

    fun <P, V, E> put(
        container: GraphData<DWT, P, V, E>,
        point: P,
        vertex: V
    ): GraphData<DWT, P, V, E>

    fun <P, V, E> put(
        container: GraphData<DWT, P, V, E>,
        point1: P,
        point2: P,
        edge: E
    ): GraphData<DWT, P, V, E> {
        return put(container, line(point1, point2), edge)
    }

    fun <P, V, E> put(
        container: GraphData<DWT, P, V, E>,
        line: Line<P>,
        edge: E
    ): GraphData<DWT, P, V, E>

    fun <P, V, E, M : Map<P, V>> putAllVertices(
        container: GraphData<DWT, P, V, E>,
        vertices: M
    ): GraphData<DWT, P, V, E> {
        return putAllVertices(container, vertices.entries.stream().map { (p: P, v: V) -> p to v })
    }

    fun <P, V, E, S : Stream<out Pair<P, V>>> putAllVertices(
        container: GraphData<DWT, P, V, E>,
        verticesStream: S
    ): GraphData<DWT, P, V, E> {
        return verticesStream
            .sequential()
            .reduce(
                container,
                { c: GraphData<DWT, P, V, E>, (p: P, v: V) -> put(c, p, v) },
                { c1: GraphData<DWT, P, V, E>, _: GraphData<DWT, P, V, E> -> c1 }
            )
    }

    fun <P, V, E, M : Map<Line<P>, E>> putAllEdges(
        container: GraphData<DWT, P, V, E>,
        edges: M
    ): GraphData<DWT, P, V, E> {
        return putAllEdges(container, edges.entries.stream().map { (l: Line<P>, e: E) -> l to e })
    }

    fun <P, V, E, S : Set<E>, M : Map<Line<P>, S>> putAllEdgeSets(
        container: GraphData<DWT, P, V, E>,
        edges: M
    ): GraphData<DWT, P, V, E> {
        return putAllEdges(
            container,
            edges.entries.stream().flatMap { (l: Line<P>, es: S) ->
                es.stream().map { e: E -> l to e }
            }
        )
    }

    fun <P, V, E, S : Stream<out Pair<Line<P>, E>>> putAllEdges(
        container: GraphData<DWT, P, V, E>,
        edgesStream: S
    ): GraphData<DWT, P, V, E> {
        return edgesStream
            .sequential()
            .reduce(
                container,
                { c: GraphData<DWT, P, V, E>, (l: Line<P>, e: E) -> put(c, l, e) },
                { c1: GraphData<DWT, P, V, E>, _: GraphData<DWT, P, V, E> -> c1 }
            )
    }

    fun <P, V, E, S : Set<E>, ST : Stream<out Pair<Line<P>, S>>> putAllEdgeSets(
        container: GraphData<DWT, P, V, E>,
        edgeSetStream: ST
    ): GraphData<DWT, P, V, E> {
        return putAllEdges(
            container,
            edgeSetStream.flatMap { (l: Line<P>, es: S) -> es.stream().map { e: E -> l to e } }
        )
    }

    fun <P, V, E> removeVertex(
        container: GraphData<DWT, P, V, E>,
        point: P
    ): GraphData<DWT, P, V, E>

    fun <P, V, E> removeEdges(
        container: GraphData<DWT, P, V, E>,
        point1: P,
        point2: P
    ): GraphData<DWT, P, V, E> {
        return removeEdges(container, line(point1, point2))
    }

    fun <P, V, E> removeEdges(
        container: GraphData<DWT, P, V, E>,
        line: Line<P>
    ): GraphData<DWT, P, V, E>

    /** Monadic Methods */
    fun <P, V, E> filterVertices(
        container: GraphData<DWT, P, V, E>,
        function: (P, V) -> Boolean
    ): GraphData<DWT, P, V, E> {
        return flatMapVertices(container) { p: P, v: V ->
            if (function(p, v)) {
                mapOf(p to v)
            } else {
                emptyMap()
            }
        }
    }

    fun <P, V, E> filterEdges(
        container: GraphData<DWT, P, V, E>,
        function: (Line<P>, E) -> Boolean
    ): GraphData<DWT, P, V, E> {
        return flatMapEdges(container) { l: Line<P>, e ->
            if (function(l, e)) {
                mapOf(l to e)
            } else {
                emptyMap()
            }
        }
    }

    fun <P, V, E, P1> mapPoints(
        container: GraphData<DWT, P, V, E>,
        function: (P, V) -> P1,
    ): GraphData<DWT, P1, V, E> {
        return flatMapVertices(container) { p: P, v: V -> mapOf(function(p, v) to v) }
    }

    fun <P, V, E, P1> mapPoints(
        container: GraphData<DWT, P, V, E>,
        function: (P) -> P1
    ): GraphData<DWT, P1, V, E> {
        return flatMapVertices(container) { p: P, v: V -> mapOf(function(p) to v) }
    }

    fun <P, V, E, V1> mapVertices(
        container: GraphData<DWT, P, V, E>,
        function: (P, V) -> V1
    ): GraphData<DWT, P, V1, E> {
        return flatMapVertices(container) { p: P, v: V -> mapOf(p to function(p, v)) }
    }

    fun <P, V, E, R> mapEdges(
        container: GraphData<DWT, P, V, E>,
        function: (Line<P>, E) -> R
    ): GraphData<DWT, P, V, R> {
        return flatMapEdges(container) { l: Line<P>, e: E -> mapOf(l to function(l, e)) }
    }

    /**
     * Takes the cartesian product of all new mappings
     *
     * Example:
     *
     * Assuming
     * - graph of type PersistentGraph<Int, Char, Double>
     * ```
     *      - with vertices { (1, 'A'), (2, 'B') } and edge { ( (1, 2), 0.2 ) }
     * ```
     * - a flatmap function of type (Int, Char) -> Map<String, Char>
     * - a lambda implementation of {(p: Int, v: Char) -> mapOf(p.toString() to v, (p *
     * 10).toString() to (v + 2)) }
     *
     * We would expect the result to be a graph with:
     * - vertices { ("1", 'A'), ("10", 'C'), ("2", 'B'), ("20", 'D') } and
     * - edges { (("1", "2"), 0.2), (("1", "20"), 0.2), (("10", "2"), 0.2), (("10", "20"), 0.2) }
     */
    fun <P, V, E, P1, V1, M : Map<out P1, V1>> flatMapVertices(
        container: GraphData<DWT, P, V, E>,
        function: (P, V) -> M,
    ): GraphData<DWT, P1, V1, E> {
        return when {
            /** If there aren't any vertices, there shouldn't be any edges */
            container === empty<P, V, E>() || vertexCount(container) == 0 -> {
                empty<P1, V1, E>()
            }
            else -> {
                putAllEdges(
                    putAllVertices(
                        empty(),
                        streamVertices(container).flatMap { (p: P, v: V) ->
                            function(p, v).entries.stream().map { (p1: P1, v1: V1) -> p1 to v1 }
                        }
                    ),
                    streamEdges(container).flatMap { (l: Line<P>, e: E) ->
                        val (p1: P, p2: P) = l
                        when (val v1: V? = get(container, p1)) {
                            null -> {
                                Stream.empty()
                            }
                            else -> {
                                when (val v2: V? = get(container, p2)) {
                                    null -> {
                                        Stream.empty()
                                    }
                                    else -> {
                                        val newSecondVertexMappings: M = function(p2, v2)
                                        function(p1, v1).entries.stream().flatMap {
                                            (newFirstPoint: P1, _: V1) ->
                                            newSecondVertexMappings.entries.stream().map {
                                                (newSecondPoint: P1, _: V1) ->
                                                line(newFirstPoint, newSecondPoint) to e
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    fun <P, V, E, E1, M : Map<Line<P>, E1>> flatMapEdges(
        container: GraphData<DWT, P, V, E>,
        function: (Line<P>, E) -> M
    ): GraphData<DWT, P, V, E1> {
        return when {
            /** If there aren't any vertices, there shouldn't be any edges */
            container === empty<P, V, E>() || vertexCount(container) == 0 -> {
                empty<P, V, E1>()
            }
            else -> {
                putAllEdges(
                    putAllVertices(empty(), verticesByPoint(container)),
                    streamEdges(container).flatMap { (l: Line<P>, e: E) ->
                        function(l, e).entries.stream().map { (l1: Line<P>, e1: E1) -> l1 to e1 }
                    }
                )
            }
        }
    }

    fun <P, V, E, V1, M : Map<P, V1>, V2> zipVertices(
        container: GraphData<DWT, P, V, E>,
        other: M,
        function: (V, V1) -> V2
    ): GraphData<DWT, P, V2, E> {
        return putAllEdges(
            putAllVertices(
                empty(),
                other.entries.stream().flatMap { (p: P, v1: V1) ->
                    when (val v: V? = get(container, p)) {
                        null -> Stream.empty()
                        else -> Stream.of(p to function(v, v1))
                    }
                }
            ),
            streamEdges(container)
        )
    }

    fun <P, V, E, E1, M : Map<Line<P>, E1>, E2> zipEdges(
        container: GraphData<DWT, P, V, E>,
        other: M,
        function: (E, E1) -> E2
    ): GraphData<DWT, P, V, E2> {
        return putAllEdges(
            putAllVertices(empty(), verticesByPoint(container)),
            other.entries.stream().flatMap { (l: Line<P>, e1: E1) ->
                when (val es: Iterable<E> = get(container, l)) {
                    is PersistentSet<E> -> es.stream()
                    else -> StreamSupport.stream(es.spliterator(), false)
                }.map { e: E -> l to function(e, e1) }
            }
        )
    }

    /** Fold Methods */
    fun <P, V, E, T> foldLeftVertices(
        container: GraphData<DWT, P, V, E>,
        initial: T,
        accumulator: (T, Pair<P, V>) -> T
    ): T {
        return vertices(container).fold(initial, accumulator)
    }

    fun <P, V, E, T> foldLeftEdges(
        container: GraphData<DWT, P, V, E>,
        initial: T,
        accumulator: (T, Pair<Line<P>, E>) -> T
    ): T {
        return edges(container).fold(initial, accumulator)
    }

    fun <P, V, E, T> foldRightVertices(
        container: GraphData<DWT, P, V, E>,
        initial: T,
        accumulator: (Pair<P, V>, T) -> T
    ): T {
        return vertices(container).reversed().fold(initial) { acc: T, pv: Pair<P, V> ->
            accumulator(pv, acc)
        }
    }

    fun <P, V, E, T> foldRightEdges(
        container: GraphData<DWT, P, V, E>,
        initial: T,
        accumulator: (Pair<Line<P>, E>, T) -> T
    ): T {
        return edges(container).reversed().fold(initial) { acc: T, le: Pair<Line<P>, E> ->
            accumulator(le, acc)
        }
    }
}
