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
        verticesByPoint: PersistentMap<P, V>,
        edgesByPointPair: PersistentMap<Pair<P, P>, E>
    ): PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E> {
        return PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph(
            verticesByPoint = verticesByPoint,
            edgesSetByPointPair =
                edgesByPointPair.asIterable().fold(
                    persistentMapOf<Pair<P, P>, PersistentSet<E>>()
                ) { pm, (ek: Pair<P, P>, e: E) -> pm.put(ek, persistentSetOf(e)) }
        )
    }

    override fun <P, V, E> fromVerticesAndEdgeSets(
        verticesByPoint: PersistentMap<P, V>,
        edgesSetByPointPair: PersistentMap<Pair<P, P>, PersistentSet<E>>
    ): PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E> {
        return PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph(
            verticesByPoint = verticesByPoint,
            edgesSetByPointPair = edgesSetByPointPair
        )
    }

    override fun <P, V, E> fromVertexAndEdgeStreams(
        verticesByPointStream: Stream<Pair<P, V>>,
        edgesByPointPairStream: Stream<Pair<Pair<P, P>, E>>,
    ): PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E> {
        val verticesByPoint: PersistentMap<P, V> =
            verticesByPointStream.reducePairsToPersistentMap()
        val edgeSetsByPathPair: PersistentMap<Pair<P, P>, PersistentSet<E>> =
            edgesByPointPairStream
                .filter { (ek, _) -> ek.first in verticesByPoint && ek.second in verticesByPoint }
                .reducePairsToPersistentSetValueMap()
        return PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph<P, V, E>(
            verticesByPoint = verticesByPoint,
            edgesSetByPointPair = edgeSetsByPathPair
        )
    }

    override fun <P, V, E> put(
        point: P,
        vertex: V,
        container: PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E> {
        return fromVerticesAndEdgeSets(
            container.narrowed().verticesByPoint.put(point, vertex),
            container.narrowed().edgesSetByPointPair
        )
    }

    override fun <P, V, E> put(
        point1: P,
        point2: P,
        edge: E,
        container: PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E> {
        val verticesByPoint = container.narrowed().verticesByPoint
        return if (point1 in verticesByPoint && point2 in verticesByPoint) {
            val edgesSetByPathPair = container.narrowed().edgesSetByPointPair
            val pathPair = point1 to point2
            fromVerticesAndEdgeSets(
                verticesByPoint,
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
        pointPair: Pair<P, P>,
        edge: E,
        container: PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E> {
        val verticesByPoint = container.narrowed().verticesByPoint
        return if (pointPair.first in verticesByPoint && pointPair.second in verticesByPoint) {
            val edgesSetByPathPair = container.narrowed().edgesSetByPointPair
            fromVerticesAndEdgeSets(
                verticesByPoint,
                edgesSetByPathPair.put(
                    pointPair,
                    edgesSetByPathPair.getOrElse(pointPair) { -> persistentSetOf() }.add(edge)
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
            container.narrowed().verticesByPoint.putAll(vertices),
            container.narrowed().edgesSetByPointPair
        )
    }

    override fun <P, V, E, M : Map<Pair<P, P>, E>> putAllEdges(
        edges: M,
        container: PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E> {
        val verticesByPoint = container.narrowed().verticesByPoint
        val updatedEdges =
            edges.entries
                .parallelStream()
                .filter { (ek: Pair<P, P>, _: E) ->
                    ek.first in verticesByPoint && ek.second in verticesByPoint
                }
                .reduceEntriesToPersistentSetValueMap(container.narrowed().edgesSetByPointPair)
        return fromVerticesAndEdgeSets(verticesByPoint, updatedEdges)
    }

    override fun <P, V, E, S : Set<E>, M : Map<Pair<P, P>, S>> putAllEdgeSets(
        edges: M,
        container: PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E> {
        val verticesByPoint = container.narrowed().verticesByPoint
        val updatedEdges =
            edges.entries
                .parallelStream()
                .filter { (ek: Pair<P, P>, _: S) ->
                    ek.first in verticesByPoint && ek.second in verticesByPoint
                }
                .reduce(
                    container.narrowed().edgesSetByPointPair,
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
        return fromVerticesAndEdgeSets(verticesByPoint, updatedEdges)
    }

    override fun <P, V, E> filterVertices(
        function: (P, V) -> Boolean,
        container: PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E> {
        val updatedVertices =
            container
                .narrowed()
                .verticesByPoint
                .entries
                .parallelStream()
                .filter { (p: P, v: V) -> function(p, v) }
                .reduceEntriesToPersistentMap()
        val updatedEdges =
            container
                .narrowed()
                .edgesSetByPointPair
                .entries
                .parallelStream()
                .filter { (ek: Pair<P, P>, _: PersistentSet<E>) ->
                    ek.first in updatedVertices && ek.second in updatedVertices
                }
                .reduce(
                    persistentMapOf<Pair<P, P>, PersistentSet<E>>(),
                    { pm, (k, v) -> pm.put(k, pm.getOrElse(k) { -> persistentSetOf() }.addAll(v)) },
                    { pm1, pm2 ->
                        val pm1Builder = pm1.builder()
                        pm2.forEach { (ek: Pair<P, P>, edges: PersistentSet<E>) ->
                            pm1Builder[ek] =
                                pm1Builder.getOrElse(ek) { -> persistentSetOf() }.addAll(edges)
                        }
                        pm1Builder.build()
                    }
                )
        return fromVerticesAndEdgeSets(updatedVertices, updatedEdges)
    }

    override fun <P, V, E> filterEdges(
        function: (Pair<P, P>, E) -> Boolean,
        container: PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E> {
        val updatedEdges =
            container
                .narrowed()
                .edgesSetByPointPair
                .entries
                .parallelStream()
                .flatMap { (ek: Pair<P, P>, edges: PersistentSet<E>) ->
                    edges.stream().map { e: E -> ek to e }
                }
                .filter { (ek: Pair<P, P>, e: E) -> function(ek, e) }
                .reducePairsToPersistentSetValueMap()
        return fromVerticesAndEdgeSets(container.narrowed().verticesByPoint, updatedEdges)
    }

    override fun <P, V, E, R> mapVertices(
        function: (P, V) -> R,
        container: PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, R, E> {
        val updatedVertices =
            container
                .narrowed()
                .verticesByPoint
                .entries
                .parallelStream()
                .map { (p: P, v: V) -> p to function(p, v) }
                .reducePairsToPersistentMap()
        val updatedEdges =
            container
                .narrowed()
                .edgesSetByPointPair
                .entries
                .parallelStream()
                .filter { (ek: Pair<P, P>, _: PersistentSet<E>) ->
                    ek.first in updatedVertices && ek.second in updatedVertices
                }
                .reduce(
                    persistentMapOf<Pair<P, P>, PersistentSet<E>>(),
                    { pm, (k, v) -> pm.put(k, pm.getOrElse(k) { -> persistentSetOf() }.addAll(v)) },
                    { pm1, pm2 ->
                        val pm1Builder = pm1.builder()
                        pm2.forEach { (ek: Pair<P, P>, edges: PersistentSet<E>) ->
                            pm1Builder[ek] =
                                pm1Builder.getOrElse(ek) { -> persistentSetOf() }.addAll(edges)
                        }
                        pm1Builder.build()
                    }
                )
        return fromVerticesAndEdgeSets(updatedVertices, updatedEdges)
    }

    override fun <P, V, E, R> mapEdges(
        function: (Pair<P, P>, E) -> R,
        container: PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, R> {
        val updatedEdges =
            container
                .narrowed()
                .edgesSetByPointPair
                .entries
                .parallelStream()
                .flatMap { (ek: Pair<P, P>, edges: PersistentSet<E>) ->
                    edges.stream().map { e: E -> ek to function(ek, e) }
                }
                .reduce(
                    persistentMapOf<Pair<P, P>, PersistentSet<R>>(),
                    { pm, (k, v) -> pm.put(k, pm.getOrElse(k) { -> persistentSetOf() }.add(v)) },
                    { pm1, pm2 ->
                        val pm1Builder = pm1.builder()
                        pm2.forEach { (ek: Pair<P, P>, edges: PersistentSet<R>) ->
                            pm1Builder[ek] =
                                pm1Builder.getOrElse(ek) { -> persistentSetOf() }.addAll(edges)
                        }
                        pm1Builder.build()
                    }
                )
        return fromVerticesAndEdgeSets(container.narrowed().verticesByPoint, updatedEdges)
    }

    override fun <P, V, E, R, M : Map<out P, R>> flatMapVertices(
        function: (P, V) -> M,
        container: PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<ParallelizableEdgeDirectedGraphWT, P, R, E> {
        val updatedVertices =
            container
                .narrowed()
                .verticesByPoint
                .entries
                .parallelStream()
                .flatMap { (p: P, v: V) -> function(p, v).entries.stream() }
                .reduceEntriesToPersistentMap()
        val updatedEdges =
            container
                .narrowed()
                .edgesSetByPointPair
                .entries
                .parallelStream()
                .filter { (ek: Pair<P, P>, _: PersistentSet<E>) ->
                    ek.first in updatedVertices && ek.second in updatedVertices
                }
                .reduce(
                    persistentMapOf<Pair<P, P>, PersistentSet<E>>(),
                    { pm, (k, v) -> pm.put(k, pm.getOrElse(k) { -> persistentSetOf() }.addAll(v)) },
                    { pm1, pm2 ->
                        val pm1Builder = pm1.builder()
                        pm2.forEach { (k: Pair<P, P>, v: PersistentSet<E>) ->
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
        val verticesByPoint = container.narrowed().verticesByPoint
        val updatedEdges =
            container
                .narrowed()
                .edgesSetByPointPair
                .entries
                .parallelStream()
                .flatMap { (ek: Pair<P, P>, edges: PersistentSet<E>) ->
                    edges.stream().map { e: E -> ek to e }
                }
                .flatMap { (ek: Pair<P, P>, e: E) -> function(ek, e).entries.stream() }
                .filter { (ek: Pair<P, P>, _: R) ->
                    ek.first in verticesByPoint && ek.second in verticesByPoint
                }
                .reduceEntriesToPersistentSetValueMap()
        return fromVerticesAndEdgeSets(verticesByPoint, updatedEdges)
    }
}
