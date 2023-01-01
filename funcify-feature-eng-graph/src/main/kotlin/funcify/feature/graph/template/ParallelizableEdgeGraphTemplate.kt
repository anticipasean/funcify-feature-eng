package funcify.feature.graph.template

import funcify.feature.graph.container.PersistentGraphContainer
import funcify.feature.graph.container.PersistentGraphContainerFactory
import funcify.feature.graph.container.PersistentGraphContainerFactory.ParallelizableEdgeGraph.Companion.ParallelizableEdgeGraphWT
import funcify.feature.graph.container.PersistentGraphContainerFactory.narrowed
import java.util.stream.Stream
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

internal interface ParallelizableEdgeGraphTemplate :
    PersistentGraphTemplate<ParallelizableEdgeGraphWT> {

    override fun <P, V, E> fromVerticesAndEdges(
        verticesByPath: PersistentMap<P, V>,
        edgesByPathPair: PersistentMap<Pair<P, P>, E>
    ): PersistentGraphContainer<ParallelizableEdgeGraphWT, P, V, E> {
        return PersistentGraphContainerFactory.ParallelizableEdgeGraph(
            verticesByPath = verticesByPath,
            edgesSetByPathPair =
                edgesByPathPair.asIterable().fold(
                    persistentMapOf<Pair<P, P>, PersistentSet<E>>()
                ) { pm, e -> pm.put(e.key, persistentSetOf(e.value)) }
        )
    }

    override fun <P, V, E> fromVerticesAndEdgeSets(
        verticesByPath: PersistentMap<P, V>,
        edgesSetByPathPair: PersistentMap<Pair<P, P>, PersistentSet<E>>
    ): PersistentGraphContainer<ParallelizableEdgeGraphWT, P, V, E> {
        return PersistentGraphContainerFactory.ParallelizableEdgeGraph(
            verticesByPath = verticesByPath,
            edgesSetByPathPair = edgesSetByPathPair
        )
    }

    override fun <P, V, E> fromVertexAndEdgeStreams(
        verticesByPathStream: Stream<Pair<P, V>>,
        edgesByPathPairStream: Stream<Pair<Pair<P, P>, E>>,
    ): PersistentGraphContainer<ParallelizableEdgeGraphWT, P, V, E> {
        val verticesByPath: PersistentMap<P, V> =
            verticesByPathStream.reduce(
                persistentMapOf<P, V>(),
                { pm, (k, v) -> pm.put(k, v) },
                PersistentMap<P, V>::putAll
            )
        val edgeSetsByPathPair =
            edgesByPathPairStream
                .filter { (ek, _) -> ek.first in verticesByPath && ek.second in verticesByPath }
                .reduce(
                    persistentMapOf<Pair<P, P>, PersistentSet<E>>(),
                    { pm, (ek, e) -> pm.put(ek, pm.getOrElse(ek) { persistentSetOf<E>() }.add(e)) },
                    { pm1, pm2 ->
                        val pm1Builder = pm1.builder()
                        pm2.forEach { (ek, es) ->
                            pm1Builder[ek] =
                                pm1Builder.getOrElse(ek) { persistentSetOf<E>() }.addAll(es)
                        }
                        pm1Builder.build()
                    }
                )
        return PersistentGraphContainerFactory.ParallelizableEdgeGraph<P, V, E>(
            verticesByPath = verticesByPath,
            edgesSetByPathPair = edgeSetsByPathPair
        )
    }

    override fun <P, V, E> put(
        path: P,
        vertex: V,
        container: PersistentGraphContainer<ParallelizableEdgeGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeGraphWT, P, V, E> {
        return fromVerticesAndEdgeSets(
            container.narrowed().verticesByPath.put(path, vertex),
            container.narrowed().edgesSetByPathPair
        )
    }

    override fun <P, V, E> put(
        path1: P,
        path2: P,
        edge: E,
        container: PersistentGraphContainer<ParallelizableEdgeGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeGraphWT, P, V, E> {
        val verticesByPath = container.narrowed().verticesByPath
        return if (path1 in verticesByPath && path2 in verticesByPath) {
            val edgesSetByPathPair = container.narrowed().edgesSetByPathPair
            val pathPair = path1 to path2
            fromVerticesAndEdgeSets(
                verticesByPath,
                edgesSetByPathPair.put(
                    pathPair,
                    edgesSetByPathPair.getOrElse(pathPair) { -> persistentSetOf() }.add(edge)
                )
            )
        } else {
            container
        }
    }

    override fun <P, V, E> put(
        pathPair: Pair<P, P>,
        edge: E,
        container: PersistentGraphContainer<ParallelizableEdgeGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeGraphWT, P, V, E> {
        val verticesByPath = container.narrowed().verticesByPath
        return if (pathPair.first in verticesByPath && pathPair.second in verticesByPath) {
            val edgesSetByPathPair = container.narrowed().edgesSetByPathPair
            fromVerticesAndEdgeSets(
                verticesByPath,
                edgesSetByPathPair.put(
                    pathPair,
                    edgesSetByPathPair.getOrElse(pathPair) { -> persistentSetOf() }.add(edge)
                )
            )
        } else {
            container
        }
    }

    override fun <P, V, E, M : Map<P, V>> putAllVertices(
        vertices: M,
        container: PersistentGraphContainer<ParallelizableEdgeGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeGraphWT, P, V, E> {
        return fromVerticesAndEdgeSets(
            container.narrowed().verticesByPath.putAll(vertices),
            container.narrowed().edgesSetByPathPair
        )
    }

    override fun <P, V, E, M : Map<Pair<P, P>, E>> putAllEdges(
        edges: M,
        container: PersistentGraphContainer<ParallelizableEdgeGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeGraphWT, P, V, E> {
        val verticesByPath = container.narrowed().verticesByPath
        val updatedEdges =
            edges.entries
                .parallelStream()
                .filter { e: Map.Entry<Pair<P, P>, E> ->
                    e.key.first in verticesByPath && e.key.second in verticesByPath
                }
                .reduce(
                    container.narrowed().edgesSetByPathPair,
                    { pm, entry ->
                        pm.put(
                            entry.key,
                            pm.getOrElse(entry.key) { -> persistentSetOf() }.add(entry.value)
                        )
                    },
                    { pm1, pm2 ->
                        val builder = pm1.builder()
                        pm2.forEach { (k: Pair<P, P>, v: PersistentSet<E>) ->
                            builder.getOrElse(k) { -> persistentSetOf() }.addAll(v)
                        }
                        builder.build()
                    }
                )
        return fromVerticesAndEdgeSets(verticesByPath, updatedEdges)
    }

    override fun <P, V, E, S : Set<E>, M : Map<Pair<P, P>, S>> putAllEdgeSets(
        edges: M,
        container: PersistentGraphContainer<ParallelizableEdgeGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeGraphWT, P, V, E> {
        val verticesByPath = container.narrowed().verticesByPath
        val updatedEdges =
            edges.entries
                .parallelStream()
                .filter { e: Map.Entry<Pair<P, P>, S> ->
                    e.key.first in verticesByPath && e.key.second in verticesByPath
                }
                .reduce(
                    container.narrowed().edgesSetByPathPair,
                    { pm, entry ->
                        pm.put(
                            entry.key,
                            pm.getOrElse(entry.key) { -> persistentSetOf() }.addAll(entry.value)
                        )
                    },
                    { pm1, pm2 ->
                        val builder = pm1.builder()
                        pm2.forEach { (k: Pair<P, P>, v: PersistentSet<E>) ->
                            builder.getOrElse(k) { -> persistentSetOf() }.addAll(v)
                        }
                        builder.build()
                    }
                )
        return fromVerticesAndEdgeSets(verticesByPath, updatedEdges)
    }

    override fun <P, V, E> filterVertices(
        function: (V) -> Boolean,
        container: PersistentGraphContainer<ParallelizableEdgeGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeGraphWT, P, V, E> {
        val updatedVertices =
            container
                .narrowed()
                .verticesByPath
                .entries
                .parallelStream()
                .filter { e: Map.Entry<P, V> -> function.invoke(e.value) }
                .reduce(
                    persistentMapOf<P, V>(),
                    { pm, entry -> pm.put(entry.key, entry.value) },
                    { pm1, pm2 -> pm1.putAll(pm2) }
                )
        val updatedEdges =
            container
                .narrowed()
                .edgesSetByPathPair
                .entries
                .parallelStream()
                .filter { e: Map.Entry<Pair<P, P>, PersistentSet<E>> ->
                    e.key.first in updatedVertices && e.key.second in updatedVertices
                }
                .reduce(
                    persistentMapOf<Pair<P, P>, PersistentSet<E>>(),
                    { pm, entry ->
                        pm.put(
                            entry.key,
                            pm.getOrElse(entry.key) { -> persistentSetOf() }.addAll(entry.value)
                        )
                    },
                    { pm1, pm2 ->
                        val builder = pm1.builder()
                        pm2.forEach { (k, v) ->
                            builder.getOrElse(k) { -> persistentSetOf() }.addAll(v)
                        }
                        builder.build()
                    }
                )
        return fromVerticesAndEdgeSets(updatedVertices, updatedEdges)
    }

    override fun <P, V, E> filterEdges(
        function: (E) -> Boolean,
        container: PersistentGraphContainer<ParallelizableEdgeGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeGraphWT, P, V, E> {
        val updatedEdges =
            container
                .narrowed()
                .edgesSetByPathPair
                .entries
                .parallelStream()
                .flatMap { entry: Map.Entry<Pair<P, P>, PersistentSet<E>> ->
                    entry.value.stream().map { e: E -> entry.key to e }
                }
                .filter { edgeForPathPair: Pair<Pair<P, P>, E> ->
                    function.invoke(edgeForPathPair.second)
                }
                .reduce(
                    persistentMapOf<Pair<P, P>, PersistentSet<E>>(),
                    { pm, pair ->
                        pm.put(
                            pair.first,
                            pm.getOrElse(pair.first) { -> persistentSetOf() }.add(pair.second)
                        )
                    },
                    { pm1, pm2 ->
                        val builder = pm1.builder()
                        pm2.forEach { (k, v) ->
                            builder.getOrElse(k) { -> persistentSetOf() }.addAll(v)
                        }
                        builder.build()
                    }
                )
        return fromVerticesAndEdgeSets(container.narrowed().verticesByPath, updatedEdges)
    }

    override fun <P, V, E, R> mapVertices(
        function: (V) -> R,
        container: PersistentGraphContainer<ParallelizableEdgeGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeGraphWT, P, R, E> {
        val updatedVertices =
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
        val updatedEdges =
            container
                .narrowed()
                .edgesSetByPathPair
                .entries
                .parallelStream()
                .filter { e: Map.Entry<Pair<P, P>, PersistentSet<E>> ->
                    e.key.first in updatedVertices && e.key.second in updatedVertices
                }
                .reduce(
                    persistentMapOf<Pair<P, P>, PersistentSet<E>>(),
                    { pm, entry ->
                        pm.put(
                            entry.key,
                            pm.getOrElse(entry.key) { -> persistentSetOf() }.addAll(entry.value)
                        )
                    },
                    { pm1, pm2 ->
                        val builder = pm1.builder()
                        pm2.forEach { (k, v) ->
                            builder.getOrElse(k) { -> persistentSetOf() }.addAll(v)
                        }
                        builder.build()
                    }
                )
        return fromVerticesAndEdgeSets(updatedVertices, updatedEdges)
    }

    override fun <P, V, E, R> mapEdges(
        function: (E) -> R,
        container: PersistentGraphContainer<ParallelizableEdgeGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeGraphWT, P, V, R> {
        val updatedEdges =
            container
                .narrowed()
                .edgesSetByPathPair
                .entries
                .parallelStream()
                .flatMap { entry: Map.Entry<Pair<P, P>, PersistentSet<E>> ->
                    entry.value.stream().map { e: E -> entry.key to function.invoke(e) }
                }
                .reduce(
                    persistentMapOf<Pair<P, P>, PersistentSet<R>>(),
                    { pm, pair ->
                        pm.put(
                            pair.first,
                            pm.getOrElse(pair.first) { -> persistentSetOf() }.add(pair.second)
                        )
                    },
                    { pm1, pm2 ->
                        val builder = pm1.builder()
                        pm2.forEach { (k, v) ->
                            builder.getOrElse(k) { -> persistentSetOf() }.addAll(v)
                        }
                        builder.build()
                    }
                )
        return fromVerticesAndEdgeSets(container.narrowed().verticesByPath, updatedEdges)
    }

    override fun <P, V, E, R, M : Map<out P, R>> flatMapVertices(
        function: (P, V) -> M,
        container: PersistentGraphContainer<ParallelizableEdgeGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeGraphWT, P, R, E> {
        val updatedVertices =
            container
                .narrowed()
                .verticesByPath
                .entries
                .parallelStream()
                .map { e: Map.Entry<P, V> -> function.invoke(e.key, e.value) }
                .flatMap { resultMap: M -> resultMap.entries.stream() }
                .reduce(
                    persistentMapOf<P, R>(),
                    { pm, entry -> pm.put(entry.key, entry.value) },
                    { pm1, pm2 -> pm1.putAll(pm2) }
                )
        val updatedEdges =
            container
                .narrowed()
                .edgesSetByPathPair
                .entries
                .parallelStream()
                .filter { e: Map.Entry<Pair<P, P>, PersistentSet<E>> ->
                    e.key.first in updatedVertices && e.key.second in updatedVertices
                }
                .reduce(
                    persistentMapOf<Pair<P, P>, PersistentSet<E>>(),
                    { pm, entry ->
                        pm.put(
                            entry.key,
                            pm.getOrElse(entry.key) { -> persistentSetOf() }.addAll(entry.value)
                        )
                    },
                    { pm1, pm2 ->
                        val builder = pm1.builder()
                        pm2.forEach { (k, v) ->
                            builder.getOrElse(k) { -> persistentSetOf() }.addAll(v)
                        }
                        builder.build()
                    }
                )
        return fromVerticesAndEdgeSets(updatedVertices, updatedEdges)
    }

    override fun <P, V, E, R, M : Map<out Pair<P, P>, R>> flatMapEdges(
        function: (Pair<P, P>, E) -> M,
        container: PersistentGraphContainer<ParallelizableEdgeGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeGraphWT, P, V, R> {
        val verticesByPath = container.narrowed().verticesByPath
        val updatedEdges =
            container
                .narrowed()
                .edgesSetByPathPair
                .entries
                .parallelStream()
                .flatMap { entry: Map.Entry<Pair<P, P>, PersistentSet<E>> ->
                    entry.value.stream().map { e: E -> entry.key to e }
                }
                .flatMap { pair: Pair<Pair<P, P>, E> ->
                    function.invoke(pair.first, pair.second).entries.stream()
                }
                .filter { entry: Map.Entry<Pair<P, P>, R> ->
                    entry.key.first in verticesByPath && entry.key.second in verticesByPath
                }
                .reduce(
                    persistentMapOf<Pair<P, P>, PersistentSet<R>>(),
                    { pm, entry ->
                        pm.put(
                            entry.key,
                            pm.getOrElse(entry.key) { -> persistentSetOf() }.add(entry.value)
                        )
                    },
                    { pm1, pm2 ->
                        val builder = pm1.builder()
                        pm2.forEach { (k, v) ->
                            builder.getOrElse(k) { -> persistentSetOf() }.addAll(v)
                        }
                        builder.build()
                    }
                )
        return fromVerticesAndEdgeSets(verticesByPath, updatedEdges)
    }
}
