package funcify.feature.graph.template

import funcify.feature.graph.container.PersistentGraphContainer
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import java.util.stream.Stream

/**
 * @param CWT
 * - Container witness type parameter
 */
internal interface PersistentGraphTemplate<CWT> {

    fun <P, V, E> fromVerticesAndEdges(
        verticesByPath: PersistentMap<P, V>,
        edgesByPathPair: PersistentMap<Pair<P, P>, E>
    ): PersistentGraphContainer<CWT, P, V, E>

    fun <P, V, E> fromVerticesAndEdgeSets(
        verticesByPath: PersistentMap<P, V>,
        edgesSetByPathPair: PersistentMap<Pair<P, P>, PersistentSet<E>>
    ): PersistentGraphContainer<CWT, P, V, E>

    fun <P, V, E> fromVertexAndEdgeStreams(
        verticesByPathStream: Stream<Pair<P, V>>,
        edgesByPathPairStream: Stream<Pair<Pair<P, P>, E>>
    ): PersistentGraphContainer<CWT, P, V, E>

    fun <P, V, E> put(
        path: P,
        vertex: V,
        container: PersistentGraphContainer<CWT, P, V, E>
    ): PersistentGraphContainer<CWT, P, V, E>

    fun <P, V, E> put(
        path1: P,
        path2: P,
        edge: E,
        container: PersistentGraphContainer<CWT, P, V, E>
    ): PersistentGraphContainer<CWT, P, V, E>

    fun <P, V, E> put(
        pathPair: Pair<P, P>,
        edge: E,
        container: PersistentGraphContainer<CWT, P, V, E>
    ): PersistentGraphContainer<CWT, P, V, E> {
        return put(
            path1 = pathPair.first,
            path2 = pathPair.second,
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
        function: (V) -> Boolean,
        container: PersistentGraphContainer<CWT, P, V, E>
    ): PersistentGraphContainer<CWT, P, V, E>

    fun <P, V, E> filterEdges(
        function: (E) -> Boolean,
        container: PersistentGraphContainer<CWT, P, V, E>
    ): PersistentGraphContainer<CWT, P, V, E>

    fun <P, V, E, R> mapVertices(
        function: (V) -> R,
        container: PersistentGraphContainer<CWT, P, V, E>
    ): PersistentGraphContainer<CWT, P, R, E>

    fun <P, V, E, R> mapEdges(
        function: (E) -> R,
        container: PersistentGraphContainer<CWT, P, V, E>
    ): PersistentGraphContainer<CWT, P, V, R>

    fun <P, V, E, R, M : Map<out P, R>> flatMapVertices(
        function: (P, V) -> M,
        container: PersistentGraphContainer<CWT, P, V, E>
    ): PersistentGraphContainer<CWT, P, R, E>

    fun <P, V, E, R, M : Map<out Pair<P, P>, R>> flatMapEdges(
        function: (Pair<P, P>, E) -> M,
        container: PersistentGraphContainer<CWT, P, V, E>
    ): PersistentGraphContainer<CWT, P, V, R>
}
