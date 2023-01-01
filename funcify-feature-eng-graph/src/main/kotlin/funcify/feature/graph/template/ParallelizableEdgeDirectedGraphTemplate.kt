package funcify.feature.graph.template

import funcify.feature.graph.container.PersistentGraphContainer
import funcify.feature.graph.container.PersistentGraphContainerFactory
import funcify.feature.graph.container.PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph.Companion.ParallelizableEdgeDirectedGraphWT
import funcify.feature.graph.container.PersistentGraphContainerFactory.narrowed
import funcify.feature.graph.extensions.PersistentMapExtensions.reduceEntriesToPersistentMap
import funcify.feature.graph.extensions.PersistentMapExtensions.reduceEntriesToPersistentSetValueMap
import funcify.feature.graph.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.graph.extensions.PersistentMapExtensions.reducePairsToPersistentSetValueMap
import java.util.stream.Stream
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet

internal interface ParallelizableEdgeDirectedGraphTemplate :
    PersistentGraphTemplate<ParallelizableEdgeDirectedGraphWT> {

    companion object {}

    override fun <P, V, E> fromVerticesAndEdges(
        verticesByPath: PersistentMap<P, V>,
        edgesByPathPair: PersistentMap<Pair<P, P>, E>
    ): PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E> {
        return PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph(
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
    ): PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E> {
        return PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph(
            verticesByPath = verticesByPath,
            edgesSetByPathPair = edgesSetByPathPair
        )
    }

    override fun <P, V, E> fromVertexAndEdgeStreams(
        verticesByPathStream: Stream<Pair<P, V>>,
        edgesByPathPairStream: Stream<Pair<Pair<P, P>, E>>,
    ): PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E> {
        val verticesByPath: PersistentMap<P, V> = verticesByPathStream.reducePairsToPersistentMap()
        val edgeSetsByPathPair: PersistentMap<Pair<P, P>, PersistentSet<E>> =
            edgesByPathPairStream
                .filter { (ek, _) -> ek.first in verticesByPath && ek.second in verticesByPath }
                .reducePairsToPersistentSetValueMap()
        return PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph<P, V, E>(
            verticesByPath = verticesByPath,
            edgesSetByPathPair = edgeSetsByPathPair
        )
    }

    override fun <P, V, E> put(
        path: P,
        vertex: V,
        container: PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E> {
        return fromVerticesAndEdgeSets(
            container.narrowed().verticesByPath.put(path, vertex),
            container.narrowed().edgesSetByPathPair
        )
    }

    override fun <P, V, E> put(
        path1: P,
        path2: P,
        edge: E,
        container: PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E> {
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
        container: PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E> {
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
        container: PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E> {
        return fromVerticesAndEdgeSets(
            container.narrowed().verticesByPath.putAll(vertices),
            container.narrowed().edgesSetByPathPair
        )
    }

    override fun <P, V, E, M : Map<Pair<P, P>, E>> putAllEdges(
        edges: M,
        container: PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E> {
        val verticesByPath = container.narrowed().verticesByPath
        val updatedEdges =
            edges.entries
                .parallelStream()
                .filter { (k, _): Map.Entry<Pair<P, P>, E> ->
                    k.first in verticesByPath && k.second in verticesByPath
                }
                .reduceEntriesToPersistentSetValueMap(container.narrowed().edgesSetByPathPair)
        return fromVerticesAndEdgeSets(verticesByPath, updatedEdges)
    }

    override fun <P, V, E, S : Set<E>, M : Map<Pair<P, P>, S>> putAllEdgeSets(
        edges: M,
        container: PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E> {
        val verticesByPath = container.narrowed().verticesByPath
        val updatedEdges =
            edges.entries
                .parallelStream()
                .filter { e: Map.Entry<Pair<P, P>, S> ->
                    e.key.first in verticesByPath && e.key.second in verticesByPath
                }
                .reduce(
                    container.narrowed().edgesSetByPathPair,
                    { pm, (k, v) -> pm.put(k, v.toPersistentSet()) },
                    { pm1, pm2 ->
                        val pm1Builder = pm1.builder()
                        pm2.forEach { (k: Pair<P, P>, v: PersistentSet<E>) ->
                            pm1Builder[k] =
                                pm1Builder.getOrElse(k) { -> persistentSetOf() }.addAll(v)
                        }
                        pm1Builder.build()
                    }
                )
        return fromVerticesAndEdgeSets(verticesByPath, updatedEdges)
    }

    override fun <P, V, E> filterVertices(
        function: (V) -> Boolean,
        container: PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E> {
        val updatedVertices =
            container
                .narrowed()
                .verticesByPath
                .entries
                .parallelStream()
                .filter { (_, v): Map.Entry<P, V> -> function(v) }
                .reduceEntriesToPersistentMap()
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
                    { pm, (k, v) -> pm.put(k, pm.getOrElse(k) { -> persistentSetOf() }.addAll(v)) },
                    { pm1, pm2 ->
                        val pm1Builder = pm1.builder()
                        pm2.forEach { (k, v) ->
                            pm1Builder[k] =
                                pm1Builder.getOrElse(k) { -> persistentSetOf() }.addAll(v)
                        }
                        pm1Builder.build()
                    }
                )
        return fromVerticesAndEdgeSets(updatedVertices, updatedEdges)
    }

    override fun <P, V, E> filterEdges(
        function: (E) -> Boolean,
        container: PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E> {
        val updatedEdges =
            container
                .narrowed()
                .edgesSetByPathPair
                .entries
                .parallelStream()
                .flatMap { (k, v): Map.Entry<Pair<P, P>, PersistentSet<E>> ->
                    v.stream().map { e: E -> k to e }
                }
                .filter { edgeForPathPair: Pair<Pair<P, P>, E> -> function(edgeForPathPair.second) }
                .reducePairsToPersistentSetValueMap()
        return fromVerticesAndEdgeSets(container.narrowed().verticesByPath, updatedEdges)
    }

    override fun <P, V, E, R> mapVertices(
        function: (V) -> R,
        container: PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, R, E> {
        val updatedVertices =
            container
                .narrowed()
                .verticesByPath
                .entries
                .parallelStream()
                .map { (k, v): Map.Entry<P, V> -> k to function(v) }
                .reducePairsToPersistentMap()
        val updatedEdges =
            container
                .narrowed()
                .edgesSetByPathPair
                .entries
                .parallelStream()
                .filter { (k, _): Map.Entry<Pair<P, P>, PersistentSet<E>> ->
                    k.first in updatedVertices && k.second in updatedVertices
                }
                .reduce(
                    persistentMapOf<Pair<P, P>, PersistentSet<E>>(),
                    { pm, (k, v) -> pm.put(k, pm.getOrElse(k) { -> persistentSetOf() }.addAll(v)) },
                    { pm1, pm2 ->
                        val pm1Builder = pm1.builder()
                        pm2.forEach { (k, v) ->
                            pm1Builder[k] =
                                pm1Builder.getOrElse(k) { -> persistentSetOf() }.addAll(v)
                        }
                        pm1Builder.build()
                    }
                )
        return fromVerticesAndEdgeSets(updatedVertices, updatedEdges)
    }

    override fun <P, V, E, R> mapEdges(
        function: (E) -> R,
        container: PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, R> {
        val updatedEdges =
            container
                .narrowed()
                .edgesSetByPathPair
                .entries
                .parallelStream()
                .flatMap { (k, v): Map.Entry<Pair<P, P>, PersistentSet<E>> ->
                    v.stream().map { e: E -> k to function(e) }
                }
                .reduce(
                    persistentMapOf<Pair<P, P>, PersistentSet<R>>(),
                    { pm, (k, v) -> pm.put(k, pm.getOrElse(k) { -> persistentSetOf() }.add(v)) },
                    { pm1, pm2 ->
                        val pm1Builder = pm1.builder()
                        pm2.forEach { (k, v) ->
                            pm1Builder[k] =
                                pm1Builder.getOrElse(k) { -> persistentSetOf() }.addAll(v)
                        }
                        pm1Builder.build()
                    }
                )
        return fromVerticesAndEdgeSets(container.narrowed().verticesByPath, updatedEdges)
    }

    override fun <P, V, E, R, M : Map<out P, R>> flatMapVertices(
        function: (P, V) -> M,
        container: PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, R, E> {
        val updatedVertices =
            container
                .narrowed()
                .verticesByPath
                .entries
                .parallelStream()
                .map { (k, v): Map.Entry<P, V> -> function(k, v) }
                .flatMap { resultMap: M -> resultMap.entries.stream() }
                .reduceEntriesToPersistentMap()
        val updatedEdges =
            container
                .narrowed()
                .edgesSetByPathPair
                .entries
                .parallelStream()
                .filter { (k, _): Map.Entry<Pair<P, P>, PersistentSet<E>> ->
                    k.first in updatedVertices && k.second in updatedVertices
                }
                .reduce(
                    persistentMapOf<Pair<P, P>, PersistentSet<E>>(),
                    { pm, (k, v) -> pm.put(k, pm.getOrElse(k) { -> persistentSetOf() }.addAll(v)) },
                    { pm1, pm2 ->
                        val pm1Builder = pm1.builder()
                        pm2.forEach { (k, v) ->
                            pm1Builder[k] =
                                pm1Builder.getOrElse(k) { -> persistentSetOf() }.addAll(v)
                        }
                        pm1Builder.build()
                    }
                )
        return fromVerticesAndEdgeSets(updatedVertices, updatedEdges)
    }

    override fun <P, V, E, R, M : Map<out Pair<P, P>, R>> flatMapEdges(
        function: (Pair<P, P>, E) -> M,
        container: PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, R> {
        val verticesByPath = container.narrowed().verticesByPath
        val updatedEdges =
            container
                .narrowed()
                .edgesSetByPathPair
                .entries
                .parallelStream()
                .flatMap { (k, v): Map.Entry<Pair<P, P>, PersistentSet<E>> ->
                    v.stream().map { e: E -> k to e }
                }
                .flatMap { (k, v): Pair<Pair<P, P>, E> -> function(k, v).entries.stream() }
                .filter { entry: Map.Entry<Pair<P, P>, R> ->
                    entry.key.first in verticesByPath && entry.key.second in verticesByPath
                }
                .reduceEntriesToPersistentSetValueMap()
        return fromVerticesAndEdgeSets(verticesByPath, updatedEdges)
    }
}
