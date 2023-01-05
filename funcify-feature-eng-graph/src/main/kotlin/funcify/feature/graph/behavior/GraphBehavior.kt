package funcify.feature.graph.behavior

import funcify.feature.graph.data.GraphData
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
        edgesByPointPair: PersistentMap<Pair<P, P>, E>
    ): GraphData<DWT, P, V, E>

    fun <P, V, E> fromVerticesAndEdgeSets(
        verticesByPoint: PersistentMap<P, V>,
        edgesSetByPointPair: PersistentMap<Pair<P, P>, PersistentSet<E>>
    ): GraphData<DWT, P, V, E>

    fun <P, V, E> fromVertexAndEdgeStreams(
        verticesByPointStream: Stream<Pair<P, V>>,
        edgesByPointPairStream: Stream<Pair<Pair<P, P>, E>>
    ): GraphData<DWT, P, V, E>

    fun <P, V, E> put(
        point: P,
        vertex: V,
        container: GraphData<DWT, P, V, E>
    ): GraphData<DWT, P, V, E>

    fun <P, V, E> put(
        point1: P,
        point2: P,
        edge: E,
        container: GraphData<DWT, P, V, E>
    ): GraphData<DWT, P, V, E>

    fun <P, V, E> put(
        pointPair: Pair<P, P>,
        edge: E,
        container: GraphData<DWT, P, V, E>
    ): GraphData<DWT, P, V, E> {
        return put(
            point1 = pointPair.first,
            point2 = pointPair.second,
            edge = edge,
            container = container
        )
    }

    fun <P, V, E, M : Map<P, V>> putAllVertices(
        vertices: M,
        container: GraphData<DWT, P, V, E>
    ): GraphData<DWT, P, V, E>

    fun <P, V, E, M : Map<Pair<P, P>, E>> putAllEdges(
        edges: M,
        container: GraphData<DWT, P, V, E>
    ): GraphData<DWT, P, V, E>

    fun <P, V, E, S : Set<E>, M : Map<Pair<P, P>, S>> putAllEdgeSets(
        edges: M,
        container: GraphData<DWT, P, V, E>
    ): GraphData<DWT, P, V, E>

    fun <P, V, E> remove(point: P, container: GraphData<DWT, P, V, E>): GraphData<DWT, P, V, E>

    fun <P, V, E> filterVertices(
        function: (P, V) -> Boolean,
        container: GraphData<DWT, P, V, E>
    ): GraphData<DWT, P, V, E>

    fun <P, V, E> filterEdges(
        function: (Pair<P, P>, E) -> Boolean,
        container: GraphData<DWT, P, V, E>
    ): GraphData<DWT, P, V, E>

    fun <P, V, E, R> mapPoints(
        function: (P, V) -> R,
        container: GraphData<DWT, P, V, E>
    ): GraphData<DWT, R, V, E>

    fun <P, V, E, R> mapPoints(
        function: (P) -> R,
        container: GraphData<DWT, P, V, E>
    ): GraphData<DWT, R, V, E>

    fun <P, V, E, R> mapVertices(
        function: (P, V) -> R,
        container: GraphData<DWT, P, V, E>
    ): GraphData<DWT, P, R, E>

    fun <P, V, E, R> mapEdges(
        function: (Pair<P, P>, E) -> R,
        container: GraphData<DWT, P, V, E>
    ): GraphData<DWT, P, V, R>

    fun <P, V, E, P1, V1, M : Map<out P1, V1>> flatMapVertices(
        function: (P, V) -> M,
        container: GraphData<DWT, P, V, E>
    ): GraphData<DWT, P1, V1, E>

    fun <P, V, E, E1, M : Map<out Pair<P, P>, E1>> flatMapEdges(
        function: (Pair<P, P>, E) -> M,
        container: GraphData<DWT, P, V, E>
    ): GraphData<DWT, P, V, E1>
}
