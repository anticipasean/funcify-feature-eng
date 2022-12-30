package funcify.feature.graph.template

import funcify.feature.graph.container.PersistentGraphContainer
import funcify.feature.graph.container.PersistentGraphContainerFactory.PersistentGraphStream
import funcify.feature.graph.container.PersistentGraphContainerFactory.PersistentGraphStream.Companion.GraphStreamWT
import funcify.feature.graph.container.PersistentGraphContainerFactory.narrowed
import funcify.feature.graph.spliterator.DuplicatingSpliterator
import java.util.*
import java.util.stream.Stream
import java.util.stream.StreamSupport
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf

/**
 *
 * @author smccarron
 * @created 2022-11-29
 */
internal interface PersistentGraphStreamTemplate : PersistentGraphTemplate<GraphStreamWT> {

    companion object {

        internal fun <T> Stream<T>.duplicate(): Stream<T> {
            return when (val spliterator: Spliterator<T> = this.spliterator()) {
                is DuplicatingSpliterator<T> -> {
                    val childSpliterator: Spliterator<T> = spliterator.duplicate()
                    StreamSupport.stream(
                        { childSpliterator },
                        childSpliterator.characteristics(),
                        this.isParallel
                    )
                }
                else -> {
                    StreamSupport.stream(
                        { DuplicatingSpliterator(spliterator) },
                        spliterator.characteristics(),
                        this.isParallel
                    )
                }
            }
        }
    }

    override fun <P, V, E> fromVerticesAndEdges(
        verticesByPath: PersistentMap<P, V>,
        edgesByPathPair: PersistentMap<Pair<P, P>, E>,
    ): PersistentGraphContainer<GraphStreamWT, P, V, E> {
        return PersistentGraphStream<P, V, E>(
            verticesByPathStream =
                verticesByPath.entries.stream().map { (p, v) -> p to v }.duplicate(),
            edgesByPathPairStream =
                edgesByPathPair.entries.stream().map { (ek, e) -> ek to e }.duplicate()
        )
    }

    override fun <P, V, E> fromVerticesAndEdgeSets(
        verticesByPath: PersistentMap<P, V>,
        edgesSetByPathPair: PersistentMap<Pair<P, P>, PersistentSet<E>>,
    ): PersistentGraphContainer<GraphStreamWT, P, V, E> {
        return PersistentGraphStream<P, V, E>(
            verticesByPathStream =
                verticesByPath.entries.stream().map { (p, v) -> p to v }.duplicate(),
            edgesByPathPairStream =
                edgesSetByPathPair.entries
                    .stream()
                    .flatMap { (ek, es) -> es.stream().map { e -> ek to e } }
                    .duplicate()
        )
    }

    override fun <P, V, E> fromVertexAndEdgeStreams(
        verticesByPathStream: Stream<Pair<P, V>>,
        edgesByPathPairStream: Stream<Pair<Pair<P, P>, E>>,
    ): PersistentGraphContainer<GraphStreamWT, P, V, E> {
        return PersistentGraphStream<P, V, E>(
            verticesByPathStream = verticesByPathStream,
            edgesByPathPairStream = edgesByPathPairStream
        )
    }

    override fun <P, V, E> put(
        path: P,
        vertex: V,
        container: PersistentGraphContainer<GraphStreamWT, P, V, E>,
    ): PersistentGraphContainer<GraphStreamWT, P, V, E> {
        return PersistentGraphStream<P, V, E>(
            verticesByPathStream =
                Stream.concat(
                        container.narrowed().verticesByPathStream.duplicate(),
                        Stream.of(path to vertex)
                    )
                    .duplicate(),
            edgesByPathPairStream = container.narrowed().edgesByPathPairStream.duplicate()
        )
    }

    override fun <P, V, E> put(
        path1: P,
        path2: P,
        edge: E,
        container: PersistentGraphContainer<GraphStreamWT, P, V, E>,
    ): PersistentGraphContainer<GraphStreamWT, P, V, E> {
        return PersistentGraphStream<P, V, E>(
            verticesByPathStream = container.narrowed().verticesByPathStream.duplicate(),
            edgesByPathPairStream =
                Stream.concat(
                        container.narrowed().edgesByPathPairStream.duplicate(),
                        Stream.of((path1 to path2) to edge)
                    )
                    .duplicate()
        )
    }

    override fun <P, V, E, S : Set<E>, M : Map<Pair<P, P>, S>> putAllEdgeSets(
        edges: M,
        container: PersistentGraphContainer<GraphStreamWT, P, V, E>,
    ): PersistentGraphContainer<GraphStreamWT, P, V, E> {
        return PersistentGraphStream<P, V, E>(
            verticesByPathStream = container.narrowed().verticesByPathStream.duplicate(),
            edgesByPathPairStream =
                Stream.concat(
                    container.narrowed().edgesByPathPairStream.duplicate(),
                    edges.entries
                        .stream()
                        .flatMap { (ek, es) -> es.stream().map { e -> ek to e } }
                        .duplicate()
                )
        )
    }

    override fun <P, V, E, M : Map<Pair<P, P>, E>> putAllEdges(
        edges: M,
        container: PersistentGraphContainer<GraphStreamWT, P, V, E>,
    ): PersistentGraphContainer<GraphStreamWT, P, V, E> {
        return PersistentGraphStream<P, V, E>(
            verticesByPathStream = container.narrowed().verticesByPathStream.duplicate(),
            edgesByPathPairStream =
                Stream.concat(
                        container.narrowed().edgesByPathPairStream.duplicate(),
                        edges.entries.stream().map { (ek, e) -> ek to e }.duplicate()
                    )
                    .duplicate()
        )
    }

    override fun <P, V, E, M : Map<P, V>> putAllVertices(
        vertices: M,
        container: PersistentGraphContainer<GraphStreamWT, P, V, E>,
    ): PersistentGraphContainer<GraphStreamWT, P, V, E> {
        return PersistentGraphStream<P, V, E>(
            verticesByPathStream =
                Stream.concat(
                        container.narrowed().verticesByPathStream.duplicate(),
                        vertices.entries.stream().map { (p, v) -> p to v }.duplicate()
                    )
                    .duplicate(),
            edgesByPathPairStream = container.narrowed().edgesByPathPairStream.duplicate()
        )
    }

    override fun <P, V, E, R, M : Map<out Pair<P, P>, R>> flatMapEdges(
        function: (Pair<P, P>, E) -> M,
        container: PersistentGraphContainer<GraphStreamWT, P, V, E>,
    ): PersistentGraphContainer<GraphStreamWT, P, V, R> {
        return PersistentGraphStream<P, V, R>(
            verticesByPathStream = container.narrowed().verticesByPathStream.duplicate(),
            edgesByPathPairStream =
                container.narrowed().edgesByPathPairStream.duplicate().flatMap { (ek, e) ->
                    function(ek, e).entries.stream().map { (ek1, r) -> ek1 to r }
                }
        )
    }

    override fun <P, V, E, R, M : Map<out P, R>> flatMapVertices(
        function: (P, V) -> M,
        container: PersistentGraphContainer<GraphStreamWT, P, V, E>,
    ): PersistentGraphContainer<GraphStreamWT, P, R, E> {
        return PersistentGraphStream<P, R, E>(
            verticesByPathStream =
                container.narrowed().verticesByPathStream.duplicate().flatMap { (p, v) ->
                    function(p, v).entries.stream().map { (p1, r) -> p1 to r }
                },
            edgesByPathPairStream = container.narrowed().edgesByPathPairStream.duplicate()
        )
    }

    override fun <P, V, E, R> mapEdges(
        function: (E) -> R,
        container: PersistentGraphContainer<GraphStreamWT, P, V, E>,
    ): PersistentGraphContainer<GraphStreamWT, P, V, R> {
        return PersistentGraphStream<P, V, R>(
            verticesByPathStream = container.narrowed().verticesByPathStream.duplicate(),
            edgesByPathPairStream =
                container.narrowed().edgesByPathPairStream.duplicate().map { (ek, e) ->
                    ek to function(e)
                }
        )
    }

    override fun <P, V, E, R> mapVertices(
        function: (V) -> R,
        container: PersistentGraphContainer<GraphStreamWT, P, V, E>,
    ): PersistentGraphContainer<GraphStreamWT, P, R, E> {
        return PersistentGraphStream<P, R, E>(
            verticesByPathStream =
                container.narrowed().verticesByPathStream.duplicate().map { (p, v) ->
                    p to function(v)
                },
            edgesByPathPairStream = container.narrowed().edgesByPathPairStream.duplicate()
        )
    }

    override fun <P, V, E> filterEdges(
        function: (E) -> Boolean,
        container: PersistentGraphContainer<GraphStreamWT, P, V, E>,
    ): PersistentGraphContainer<GraphStreamWT, P, V, E> {
        return PersistentGraphStream<P, V, E>(
            verticesByPathStream = container.narrowed().verticesByPathStream.duplicate(),
            edgesByPathPairStream =
                container.narrowed().edgesByPathPairStream.duplicate().filter { (_, e) ->
                    function(e)
                }
        )
    }

    override fun <P, V, E> filterVertices(
        function: (V) -> Boolean,
        container: PersistentGraphContainer<GraphStreamWT, P, V, E>,
    ): PersistentGraphContainer<GraphStreamWT, P, V, E> {
        val updatedVerticesByPath: PersistentMap<P, V> =
            container
                .narrowed()
                .verticesByPathStream
                .filter { (_, v) -> function(v) }
                .reduce(
                    persistentMapOf<P, V>(),
                    { m, (p, v) -> m.put(p, v) },
                    PersistentMap<P, V>::putAll
                )
        return PersistentGraphStream<P, V, E>(
            verticesByPathStream = updatedVerticesByPath.entries.stream().map { (p, v) -> p to v },
            edgesByPathPairStream =
                container.narrowed().edgesByPathPairStream.duplicate().filter { (ek, _) ->
                    ek.first in updatedVerticesByPath && ek.second in updatedVerticesByPath
                }
        )
    }
}
