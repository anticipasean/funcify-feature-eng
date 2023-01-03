package funcify.feature.graph.template

import funcify.feature.graph.container.PersistentGraphContainer
import funcify.feature.graph.container.PersistentGraphContainerFactory
import funcify.feature.graph.container.PersistentGraphContainerFactory.DirectedGraph.Companion.DirectedGraphWT
import funcify.feature.graph.container.PersistentGraphContainerFactory.narrowed
import funcify.feature.graph.extensions.PersistentMapExtensions.reduceEntriesToPersistentMap
import funcify.feature.graph.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import java.util.stream.Stream
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf

internal interface DirectedGraphTemplate : PersistentGraphTemplate<DirectedGraphWT> {

    override fun <P, V, E> fromVerticesAndEdges(
        verticesByPoint: PersistentMap<P, V>,
        edgesByPointPair: PersistentMap<Pair<P, P>, E>
    ): PersistentGraphContainer<DirectedGraphWT, P, V, E> {
        return PersistentGraphContainerFactory.DirectedGraph(
            verticesByPoint = verticesByPoint,
            edgesByPointPair = edgesByPointPair
        )
    }

    override fun <P, V, E> fromVerticesAndEdgeSets(
        verticesByPoint: PersistentMap<P, V>,
        edgesSetByPointPair: PersistentMap<Pair<P, P>, PersistentSet<E>>
    ): PersistentGraphContainer<DirectedGraphWT, P, V, E> {
        return PersistentGraphContainerFactory.DirectedGraph(
            verticesByPoint = verticesByPoint,
            edgesByPointPair =
                edgesSetByPointPair.entries
                    .parallelStream()
                    .flatMap { e: Map.Entry<Pair<P, P>, PersistentSet<E>> ->
                        e.value.stream().map { edge -> e.key to edge }
                    }
                    .reducePairsToPersistentMap()
        )
    }

    override fun <P, V, E> fromVertexAndEdgeStreams(
        verticesByPointStream: Stream<Pair<P, V>>,
        edgesByPointPairStream: Stream<Pair<Pair<P, P>, E>>,
    ): PersistentGraphContainer<DirectedGraphWT, P, V, E> {
        val verticesByPath: PersistentMap<P, V> =
            verticesByPointStream.reduce(
                persistentMapOf<P, V>(),
                { pm, (k, v) -> pm.put(k, v) },
                PersistentMap<P, V>::putAll
            )
        val edgesByPathPair: PersistentMap<Pair<P, P>, E> =
            edgesByPointPairStream
                .filter { (ek, _) -> ek.first in verticesByPath && ek.second in verticesByPath }
                .reducePairsToPersistentMap()
        return PersistentGraphContainerFactory.DirectedGraph<P, V, E>(
            verticesByPoint = verticesByPath,
            edgesByPointPair = edgesByPathPair
        )
    }

    override fun <P, V, E> put(
        point: P,
        vertex: V,
        container: PersistentGraphContainer<DirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<DirectedGraphWT, P, V, E> {
        return fromVerticesAndEdges(
            container.narrowed().verticesByPoint.put(point, vertex),
            container.narrowed().edgesByPointPair
        )
    }

    override fun <P, V, E> put(
        point1: P,
        point2: P,
        edge: E,
        container: PersistentGraphContainer<DirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<DirectedGraphWT, P, V, E> {
        val verticesByPath = container.narrowed().verticesByPoint
        return if (point1 in verticesByPath && point2 in verticesByPath) {
            fromVerticesAndEdges(
                verticesByPath,
                container.narrowed().edgesByPointPair.put(point1 to point2, edge)
            )
        } else {
            container
        }
    }

    override fun <P, V, E> put(
        pointPair: Pair<P, P>,
        edge: E,
        container: PersistentGraphContainer<DirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<DirectedGraphWT, P, V, E> {
        val verticesByPath = container.narrowed().verticesByPoint
        return if (pointPair.first in verticesByPath && pointPair.second in verticesByPath) {
            fromVerticesAndEdges(
                verticesByPath,
                container.narrowed().edgesByPointPair.put(pointPair, edge)
            )
        } else {
            container
        }
    }

    override fun <P, V, E, M : Map<P, V>> putAllVertices(
        vertices: M,
        container: PersistentGraphContainer<DirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<DirectedGraphWT, P, V, E> {
        return fromVerticesAndEdges(
            container.narrowed().verticesByPoint.putAll(vertices),
            container.narrowed().edgesByPointPair
        )
    }

    override fun <P, V, E, M : Map<Pair<P, P>, E>> putAllEdges(
        edges: M,
        container: PersistentGraphContainer<DirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<DirectedGraphWT, P, V, E> {
        val verticesByPath: PersistentMap<P, V> = container.narrowed().verticesByPoint
        val updatedEdges =
            edges.entries
                .parallelStream()
                .filter { (ek, _) -> ek.first in verticesByPath && ek.second in verticesByPath }
                .reduceEntriesToPersistentMap(container.narrowed().edgesByPointPair)
        return fromVerticesAndEdges(verticesByPath, updatedEdges)
    }

    override fun <P, V, E, S : Set<E>, M : Map<Pair<P, P>, S>> putAllEdgeSets(
        edges: M,
        container: PersistentGraphContainer<DirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<DirectedGraphWT, P, V, E> {
        val verticesByPath: PersistentMap<P, V> = container.narrowed().verticesByPoint
        val updatedEdges =
            edges.entries
                .parallelStream()
                .filter { (ek, _): Map.Entry<Pair<P, P>, S> ->
                    ek.first in verticesByPath && ek.second in verticesByPath
                }
                .flatMap { (ek, edges): Map.Entry<Pair<P, P>, Set<E>> ->
                    edges.stream().map { e: E -> ek to e }
                }
                .reducePairsToPersistentMap()
        return fromVerticesAndEdges(verticesByPath, updatedEdges)
    }

    override fun <P, V, E> filterVertices(
        function: (P, V) -> Boolean,
        container: PersistentGraphContainer<DirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<DirectedGraphWT, P, V, E> {
        val updatedVertices =
            container
                .narrowed()
                .verticesByPoint
                .entries
                .stream()
                .parallel()
                .filter { (p, v): Map.Entry<P, V> -> function(p, v) }
                .reduceEntriesToPersistentMap()
        val updatedEdges =
            container
                .narrowed()
                .edgesByPointPair
                .entries
                .stream()
                .parallel()
                .filter { (ek, _): Map.Entry<Pair<P, P>, E> ->
                    ek.first in updatedVertices && ek.second in updatedVertices
                }
                .reduceEntriesToPersistentMap()
        return fromVerticesAndEdges(updatedVertices, updatedEdges)
    }

    override fun <P, V, E> filterEdges(
        function: (Pair<P, P>, E) -> Boolean,
        container: PersistentGraphContainer<DirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<DirectedGraphWT, P, V, E> {
        val updatedEdges =
            container
                .narrowed()
                .edgesByPointPair
                .entries
                .stream()
                .parallel()
                .filter { (ek, e): Map.Entry<Pair<P, P>, E> -> function(ek, e) }
                .reduceEntriesToPersistentMap()
        return fromVerticesAndEdges(container.narrowed().verticesByPoint, updatedEdges)
    }

    override fun <P, V, E, R> mapVertices(
        function: (P, V) -> R,
        container: PersistentGraphContainer<DirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<DirectedGraphWT, P, R, E> {
        val updatedVertices: PersistentMap<P, R> =
            container
                .narrowed()
                .verticesByPoint
                .entries
                .parallelStream()
                .map { (p, v): Map.Entry<P, V> -> p to function(p, v) }
                .reducePairsToPersistentMap()
        return fromVerticesAndEdges(updatedVertices, container.narrowed().edgesByPointPair)
    }

    override fun <P, V, E, R> mapEdges(
        function: (Pair<P, P>, E) -> R,
        container: PersistentGraphContainer<DirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<DirectedGraphWT, P, V, R> {
        val verticesByPath = container.narrowed().verticesByPoint
        val updatedEdges =
            container
                .narrowed()
                .edgesByPointPair
                .entries
                .parallelStream()
                .map { (ek, e): Map.Entry<Pair<P, P>, E> -> ek to function(ek, e) }
                .reducePairsToPersistentMap()
        return fromVerticesAndEdges(verticesByPath, updatedEdges)
    }

    override fun <P, V, E, R, M : Map<out P, R>> flatMapVertices(
        function: (P, V) -> M,
        container: PersistentGraphContainer<DirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<DirectedGraphWT, P, R, E> {
        val updatedVertices: PersistentMap<P, R> =
            container
                .narrowed()
                .verticesByPoint
                .entries
                .parallelStream()
                .map { (p, v): Map.Entry<P, V> -> function(p, v) }
                .flatMap { m: M -> m.entries.stream() }
                .reduceEntriesToPersistentMap()
        val updatedEdges: PersistentMap<Pair<P, P>, E> =
            container
                .narrowed()
                .edgesByPointPair
                .entries
                .parallelStream()
                .filter { (ek, _): Map.Entry<Pair<P, P>, E> ->
                    ek.first in updatedVertices && ek.second in updatedVertices
                }
                .reduceEntriesToPersistentMap()
        return fromVerticesAndEdges(updatedVertices, updatedEdges)
    }

    override fun <P, V, E, R, M : Map<out Pair<P, P>, R>> flatMapEdges(
        function: (Pair<P, P>, E) -> M,
        container: PersistentGraphContainer<DirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<DirectedGraphWT, P, V, R> {
        val vertices: PersistentMap<P, V> = container.narrowed().verticesByPoint
        val updatedEdges: PersistentMap<Pair<P, P>, R> =
            container
                .narrowed()
                .edgesByPointPair
                .entries
                .parallelStream()
                .flatMap { (ek, e): Map.Entry<Pair<P, P>, E> -> function(ek, e).entries.stream() }
                .filter { (ek, _): Map.Entry<Pair<P, P>, R> ->
                    ek.first in vertices && ek.second in vertices
                }
                .reduceEntriesToPersistentMap()
        return fromVerticesAndEdges(vertices, updatedEdges)
    }
}
