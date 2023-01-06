package funcify.feature.graph.behavior

import funcify.feature.graph.data.GraphData
import funcify.feature.graph.line.Line
import java.util.stream.Stream
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet

/**
 * @param DWT
 * - **Data** **Witness** **Type** parameter
 */
internal interface GraphBehavior<DWT> {

    fun <P, V, E> fromVerticesAndEdges(
        verticesByPoint: PersistentMap<P, V>,
        edgesByLine: PersistentMap<Line<P>, E>
    ): GraphData<DWT, P, V, E>

    fun <P, V, E> fromVerticesAndEdgeSets(
        verticesByPoint: PersistentMap<P, V>,
        edgesSetByLine: PersistentMap<Line<P>, PersistentSet<E>>
    ): GraphData<DWT, P, V, E>

    fun <P, V, E> fromVertexAndEdgeStreams(
        verticesByPointStream: Stream<Pair<P, V>>,
        edgesByLineStream: Stream<Pair<Line<P>, E>>
    ): GraphData<DWT, P, V, E>

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
    ): GraphData<DWT, P, V, E>

    fun <P, V, E> put(
        container: GraphData<DWT, P, V, E>,
        line: Line<P>,
        edge: E
    ): GraphData<DWT, P, V, E>

    fun <P, V, E, M : Map<out P, V>> putAllVertices(
        container: GraphData<DWT, P, V, E>,
        vertices: M
    ): GraphData<DWT, P, V, E>

    fun <P, V, E, M : Map<out Line<P>, E>> putAllEdges(
        container: GraphData<DWT, P, V, E>,
        edges: M
    ): GraphData<DWT, P, V, E>

    fun <P, V, E, S : Set<E>, M : Map<out Line<P>, S>> putAllEdgeSets(
        container: GraphData<DWT, P, V, E>,
        edges: M
    ): GraphData<DWT, P, V, E>

    fun <P, V, E> remove(container: GraphData<DWT, P, V, E>, point: P): GraphData<DWT, P, V, E>

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

    fun <P, V, E, P1, V1, M : Map<out P1, V1>> flatMapVertices(
        container: GraphData<DWT, P, V, E>,
        function: (P, V) -> M,
    ): GraphData<DWT, P1, V1, E>

    fun <P, V, E, E1, M : Map<out Line<P>, E1>> flatMapEdges(
        container: GraphData<DWT, P, V, E>,
        function: (Line<P>, E) -> M
    ): GraphData<DWT, P, V, E1>
}
