package funcify.feature.graph.template

import funcify.feature.graph.container.PersistentGraphContainer
import java.util.stream.Stream
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet

/**
 * @param CWT
 * - Container witness type parameter
 */
internal interface PersistentGraphTemplate<CWT> {

    fun <P, V, E> fromVerticesAndEdges(
        verticesByPoint: PersistentMap<P, V>,
        edgesByPointPair: PersistentMap<Pair<P, P>, E>
    ): PersistentGraphContainer<CWT, P, V, E>

    fun <P, V, E> fromVerticesAndEdgeSets(
        verticesByPoint: PersistentMap<P, V>,
        edgesSetByPointPair: PersistentMap<Pair<P, P>, PersistentSet<E>>
    ): PersistentGraphContainer<CWT, P, V, E>

    fun <P, V, E> fromVertexAndEdgeStreams(
        verticesByPointStream: Stream<Pair<P, V>>,
        edgesByPointPairStream: Stream<Pair<Pair<P, P>, E>>
    ): PersistentGraphContainer<CWT, P, V, E>

    fun <P, V, E> put(
        point: P,
        vertex: V,
        container: PersistentGraphContainer<CWT, P, V, E>
    ): PersistentGraphContainer<CWT, P, V, E>

    fun <P, V, E> put(
        point1: P,
        point2: P,
        edge: E,
        container: PersistentGraphContainer<CWT, P, V, E>
    ): PersistentGraphContainer<CWT, P, V, E>

    fun <P, V, E> put(
        pointPair: Pair<P, P>,
        edge: E,
        container: PersistentGraphContainer<CWT, P, V, E>
    ): PersistentGraphContainer<CWT, P, V, E> {
        return put(
            point1 = pointPair.first,
            point2 = pointPair.second,
            edge = edge,
            container = container
        )
    }

    fun <P, V, E, M : Map<P, V>> putAllVertices(
        vertices: M,
        container: PersistentGraphContainer<CWT, P, V, E>
    ): PersistentGraphContainer<CWT, P, V, E>

    fun <P, V, E, M : Map<Pair<P, P>, E>> putAllEdges(
        edges: M,
        container: PersistentGraphContainer<CWT, P, V, E>
    ): PersistentGraphContainer<CWT, P, V, E>

    fun <P, V, E, S : Set<E>, M : Map<Pair<P, P>, S>> putAllEdgeSets(
        edges: M,
        container: PersistentGraphContainer<CWT, P, V, E>
    ): PersistentGraphContainer<CWT, P, V, E>

    fun <P, V, E> filterVertices(
        function: (P, V) -> Boolean,
        container: PersistentGraphContainer<CWT, P, V, E>
    ): PersistentGraphContainer<CWT, P, V, E>

    fun <P, V, E> filterEdges(
        function: (Pair<P, P>, E) -> Boolean,
        container: PersistentGraphContainer<CWT, P, V, E>
    ): PersistentGraphContainer<CWT, P, V, E>

    fun <P, V, E, R> mapPoints(
        function: (P, V) -> R,
        container: PersistentGraphContainer<CWT, P, V, E>
    ): PersistentGraphContainer<CWT, R, V, E>

    fun <P, V, E, R> mapPoints(
        function: (P) -> R,
        container: PersistentGraphContainer<CWT, P, V, E>
    ): PersistentGraphContainer<CWT, R, V, E>

    fun <P, V, E, R> mapVertices(
        function: (P, V) -> R,
        container: PersistentGraphContainer<CWT, P, V, E>
    ): PersistentGraphContainer<CWT, P, R, E>

    fun <P, V, E, R> mapEdges(
        function: (Pair<P, P>, E) -> R,
        container: PersistentGraphContainer<CWT, P, V, E>
    ): PersistentGraphContainer<CWT, P, V, R>

    fun <P, V, E, P1, V1, M : Map<out P1, V1>> flatMapVertices(
        function: (P, V) -> M,
        container: PersistentGraphContainer<CWT, P, V, E>
    ): PersistentGraphContainer<CWT, P1, V1, E>

    fun <P, V, E, E1, M : Map<out Pair<P, P>, E1>> flatMapEdges(
        function: (Pair<P, P>, E) -> M,
        container: PersistentGraphContainer<CWT, P, V, E>
    ): PersistentGraphContainer<CWT, P, V, E1>
}
