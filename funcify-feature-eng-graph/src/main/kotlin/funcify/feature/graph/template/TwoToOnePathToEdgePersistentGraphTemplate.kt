package funcify.feature.graph.template

import funcify.feature.graph.container.PersistentGraphContainer
import funcify.feature.graph.container.PersistentGraphContainerFactory
import funcify.feature.graph.container.PersistentGraphContainerFactory.TwoToOnePathToEdgeGraph.Companion.TwoToOnePathToEdgeGraphWT
import funcify.feature.graph.container.PersistentGraphContainerFactory.narrowed
import java.util.stream.Stream
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf

internal interface TwoToOnePathToEdgePersistentGraphTemplate :
    PersistentGraphTemplate<TwoToOnePathToEdgeGraphWT> {

    override fun <P, V, E> fromVerticesAndEdges(
        verticesByPath: PersistentMap<P, V>,
        edgesByPathPair: PersistentMap<Pair<P, P>, E>
    ): PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, V, E> {
        return PersistentGraphContainerFactory.TwoToOnePathToEdgeGraph(
            verticesByPath = verticesByPath,
            edgesByPathPair = edgesByPathPair
        )
    }

    override fun <P, V, E> fromVerticesAndEdgeSets(
        verticesByPath: PersistentMap<P, V>,
        edgesSetByPathPair: PersistentMap<Pair<P, P>, PersistentSet<E>>
    ): PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, V, E> {
        return PersistentGraphContainerFactory.TwoToOnePathToEdgeGraph(
            verticesByPath = verticesByPath,
            edgesByPathPair =
                edgesSetByPathPair.entries
                    .parallelStream()
                    .flatMap { e: Map.Entry<Pair<P, P>, PersistentSet<E>> ->
                        e.value.stream().map { edge -> e.key to edge }
                    }
                    .reduce(
                        persistentMapOf<Pair<P, P>, E>(),
                        { pm, e -> pm.put(e.first, e.second) },
                        { pm1, pm2 -> pm1.putAll(pm2) }
                    )
        )
    }

    override fun <P, V, E> fromVertexAndEdgeStreams(
        verticesByPathStream: Stream<Pair<P, V>>,
        edgesByPathPairStream: Stream<Pair<Pair<P, P>, E>>,
    ): PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, V, E> {
        val verticesByPath: PersistentMap<P, V> =
            verticesByPathStream.reduce(
                persistentMapOf<P, V>(),
                { pm, (k, v) -> pm.put(k, v) },
                PersistentMap<P, V>::putAll
            )
        val edgesByPathPair =
            edgesByPathPairStream
                .filter { (ek, _) -> ek.first in verticesByPath && ek.second in verticesByPath }
                .reduce(
                    persistentMapOf<Pair<P, P>, E>(),
                    { pm, (ek, e) -> pm.put(ek, e) },
                    PersistentMap<Pair<P, P>, E>::putAll
                )
        return PersistentGraphContainerFactory.TwoToOnePathToEdgeGraph<P, V, E>(
            verticesByPath = verticesByPath,
            edgesByPathPair = edgesByPathPair
        )
    }

    override fun <P, V, E> put(
        path: P,
        vertex: V,
        container: PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, V, E>
    ): PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, V, E> {
        return fromVerticesAndEdges(
            container.narrowed().verticesByPath.put(path, vertex),
            container.narrowed().edgesByPathPair
        )
    }

    override fun <P, V, E> put(
        path1: P,
        path2: P,
        edge: E,
        container: PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, V, E>
    ): PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, V, E> {
        val verticesByPath = container.narrowed().verticesByPath
        return if (path1 in verticesByPath && path2 in verticesByPath) {
            fromVerticesAndEdges(
                verticesByPath,
                container.narrowed().edgesByPathPair.put(path1 to path2, edge)
            )
        } else {
            container
        }
    }

    override fun <P, V, E> put(
        pathPair: Pair<P, P>,
        edge: E,
        container: PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, V, E>
    ): PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, V, E> {
        val verticesByPath = container.narrowed().verticesByPath
        return if (pathPair.first in verticesByPath && pathPair.second in verticesByPath) {
            fromVerticesAndEdges(
                verticesByPath,
                container.narrowed().edgesByPathPair.put(pathPair, edge)
            )
        } else {
            container
        }
    }

    override fun <P, V, E, M : Map<P, V>> putAllVertices(
        vertices: M,
        container: PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, V, E>
    ): PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, V, E> {
        return fromVerticesAndEdges(
            container.narrowed().verticesByPath.putAll(vertices),
            container.narrowed().edgesByPathPair
        )
    }

    override fun <P, V, E, M : Map<Pair<P, P>, E>> putAllEdges(
        edges: M,
        container: PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, V, E>
    ): PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, V, E> {
        val verticesByPath: PersistentMap<P, V> = container.narrowed().verticesByPath
        val updatedEdges =
            edges.entries
                .parallelStream()
                .filter { e -> e.key.first in verticesByPath && e.key.second in verticesByPath }
                .reduce(
                    container.narrowed().edgesByPathPair,
                    { pm, entry -> pm.put(entry.key, entry.value) },
                    { pm1, pm2 -> pm1.putAll(pm2) }
                )
        return fromVerticesAndEdges(verticesByPath, updatedEdges)
    }

    override fun <P, V, E, S : Set<E>, M : Map<Pair<P, P>, S>> putAllEdgeSets(
        edges: M,
        container: PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, V, E>
    ): PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, V, E> {
        val verticesByPath: PersistentMap<P, V> = container.narrowed().verticesByPath
        val updatedEdges =
            edges.entries
                .parallelStream()
                .filter { e: Map.Entry<Pair<P, P>, S> ->
                    e.key.first in verticesByPath && e.key.second in verticesByPath
                }
                .flatMap { entry: Map.Entry<Pair<P, P>, Set<E>> ->
                    entry.value.stream().map { e: E -> entry.key to e }
                }
                .reduce(
                    container.narrowed().edgesByPathPair,
                    { pm, pair -> pm.put(pair.first, pair.second) },
                    { pm1, pm2 -> pm1.putAll(pm2) }
                )
        return fromVerticesAndEdges(verticesByPath, updatedEdges)
    }

    override fun <P, V, E> filterVertices(
        function: (V) -> Boolean,
        container: PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, V, E>
    ): PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, V, E> {
        val updatedVertices =
            container
                .narrowed()
                .verticesByPath
                .entries
                .stream()
                .parallel()
                .filter { entry: Map.Entry<P, V> -> function.invoke(entry.value) }
                .reduce(
                    persistentMapOf<P, V>(),
                    { pm, entry -> pm.put(entry.key, entry.value) },
                    { pm1, pm2 -> pm1.putAll(pm2) }
                )
        val updatedEdges =
            container
                .narrowed()
                .edgesByPathPair
                .entries
                .stream()
                .parallel()
                .filter { e: Map.Entry<Pair<P, P>, E> ->
                    e.key.first in updatedVertices && e.key.second in updatedVertices
                }
                .reduce(
                    persistentMapOf<Pair<P, P>, E>(),
                    { pm, entry -> pm.put(entry.key, entry.value) },
                    { pm1, pm2 -> pm1.putAll(pm2) }
                )
        return fromVerticesAndEdges(updatedVertices, updatedEdges)
    }

    override fun <P, V, E> filterEdges(
        function: (E) -> Boolean,
        container: PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, V, E>
    ): PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, V, E> {
        val updatedEdges =
            container
                .narrowed()
                .edgesByPathPair
                .entries
                .stream()
                .parallel()
                .filter { entry: Map.Entry<Pair<P, P>, E> -> function.invoke(entry.value) }
                .reduce(
                    persistentMapOf<Pair<P, P>, E>(),
                    { pm, entry -> pm.put(entry.key, entry.value) },
                    { pm1, pm2 -> pm1.putAll(pm2) }
                )
        return fromVerticesAndEdges(container.narrowed().verticesByPath, updatedEdges)
    }

    override fun <P, V, E, R> mapVertices(
        function: (V) -> R,
        container: PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, V, E>
    ): PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, R, E> {
        val updatedVertices: PersistentMap<P, R> =
            container
                .narrowed()
                .verticesByPath
                .entries
                .parallelStream()
                .map { e: Map.Entry<P, V> -> e.key to function.invoke(e.value) }
                .reduce(
                    persistentMapOf<P, R>(),
                    { pm, pair -> pm.put(pair.first, pair.second) },
                    { pm1, pm2 -> pm1.putAll(pm2) }
                )
        return fromVerticesAndEdges(updatedVertices, container.narrowed().edgesByPathPair)
    }

    override fun <P, V, E, R> mapEdges(
        function: (E) -> R,
        container: PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, V, E>
    ): PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, V, R> {
        val verticesByPath = container.narrowed().verticesByPath
        val updatedEdges =
            container
                .narrowed()
                .edgesByPathPair
                .entries
                .parallelStream()
                .map { e: Map.Entry<Pair<P, P>, E> -> e.key to function.invoke(e.value) }
                .reduce(
                    persistentMapOf<Pair<P, P>, R>(),
                    { pm, pair -> pm.put(pair.first, pair.second) },
                    { pm1, pm2 -> pm1.putAll(pm2) }
                )
        return fromVerticesAndEdges(verticesByPath, updatedEdges)
    }

    override fun <P, V, E, R, M : Map<out P, R>> flatMapVertices(
        function: (P, V) -> M,
        container: PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, V, E>
    ): PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, R, E> {
        val updatedVertices: PersistentMap<P, R> =
            container
                .narrowed()
                .verticesByPath
                .entries
                .parallelStream()
                .map { e: Map.Entry<P, V> -> function.invoke(e.key, e.value) }
                .flatMap { m: M -> m.entries.stream() }
                .reduce(
                    persistentMapOf<P, R>(),
                    { pm, entry -> pm.put(entry.key, entry.value) },
                    { pm1, pm2 -> pm1.putAll(pm2) }
                )
        val updatedEdges: PersistentMap<Pair<P, P>, E> =
            container
                .narrowed()
                .edgesByPathPair
                .entries
                .parallelStream()
                .filter { e: Map.Entry<Pair<P, P>, E> ->
                    e.key.first in updatedVertices && e.key.second in updatedVertices
                }
                .reduce(
                    persistentMapOf<Pair<P, P>, E>(),
                    { pm, entry -> pm.put(entry.key, entry.value) },
                    { pm1, pm2 -> pm1.putAll(pm2) }
                )
        return fromVerticesAndEdges(updatedVertices, updatedEdges)
    }

    override fun <P, V, E, R, M : Map<out Pair<P, P>, R>> flatMapEdges(
        function: (Pair<P, P>, E) -> M,
        container: PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, V, E>
    ): PersistentGraphContainer<TwoToOnePathToEdgeGraphWT, P, V, R> {
        val vertices: PersistentMap<P, V> = container.narrowed().verticesByPath
        val updatedEdges: PersistentMap<Pair<P, P>, R> =
            container
                .narrowed()
                .edgesByPathPair
                .entries
                .parallelStream()
                .flatMap { e: Map.Entry<Pair<P, P>, E> ->
                    function.invoke(e.key, e.value).entries.stream()
                }
                .filter { e: Map.Entry<Pair<P, P>, R> ->
                    e.key.first in vertices && e.key.second in vertices
                }
                .reduce(
                    persistentMapOf<Pair<P, P>, R>(),
                    { pm, entry -> pm.put(entry.key, entry.value) },
                    { pm1, pm2 -> pm1.putAll(pm2) }
                )
        return fromVerticesAndEdges(vertices, updatedEdges)
    }
}
