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

    override fun <P, V, E, R> mapPoints(
        function: (P, V) -> R,
        container: PersistentGraphContainer<DirectedGraphWT, P, V, E>,
    ): PersistentGraphContainer<DirectedGraphWT, R, V, E> {
        val updatedVertices: PersistentMap<R, V> =
            container
                .narrowed()
                .verticesByPoint
                .entries
                .parallelStream()
                .map { (p: P, v: V) -> function(p, v) to v }
                .reducePairsToPersistentMap()
        val updatedEdges =
            container
                .narrowed()
                .edgesByPointPair
                .entries
                .parallelStream()
                .flatMap { (ek: Pair<P, P>, e: E) ->
                    when (val v1: V? = container.narrowed().verticesByPoint[ek.first]) {
                        null -> {
                            Stream.empty()
                        }
                        else -> {
                            when (val v2: V? = container.narrowed().verticesByPoint[ek.second]) {
                                null -> {
                                    Stream.empty()
                                }
                                else -> {
                                    Stream.of(
                                        (function(ek.first, v1) to function(ek.second, v2)) to e
                                    )
                                }
                            }
                        }
                    }
                }
                .reducePairsToPersistentMap()
        return fromVerticesAndEdges(updatedVertices, updatedEdges)
    }

    override fun <P, V, E, R> mapPoints(
        function: (P) -> R,
        container: PersistentGraphContainer<DirectedGraphWT, P, V, E>,
    ): PersistentGraphContainer<DirectedGraphWT, R, V, E> {
        val updatedVertices =
            container.narrowed().verticesByPoint.entries.parallelStream().map { (p: P, v: V) ->
                function(p) to v
            }
        val updatedEdges =
            container.narrowed().edgesByPointPair.entries.parallelStream().map {
                (ek: Pair<P, P>, e: E) ->
                (function(ek.first) to function(ek.second)) to e
            }
        return fromVertexAndEdgeStreams(updatedVertices, updatedEdges)
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

    override fun <P, V, E, P1, V1, M : Map<out P1, V1>> flatMapVertices(
        function: (P, V) -> M,
        container: PersistentGraphContainer<DirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<DirectedGraphWT, P1, V1, E> {
        val updatedVertices: PersistentMap<P1, V1> =
            container
                .narrowed()
                .verticesByPoint
                .entries
                .parallelStream()
                .map { (p: P, v: V) -> function(p, v) }
                .flatMap { m: M -> m.entries.stream() }
                .reduceEntriesToPersistentMap()

        /**
         * Takes the cartesian product of all new mappings
         *
         * Example:
         *
         * Assuming
         * - graph of type PersistentGraph<Int, Char, Double>
         * ```
         *      - with vertices { (1, 'A'), (2, 'B') } and edge { ( (1, 2), 0.2 ) }
         * ```
         * - a flatmap function of type (Int, Char) -> Map<String, Char>
         * - a lambda implementation of {(p: Int, v: Char) -> mapOf(p.toString() to v, (p *
         * 10).toString() to (v + 2)) }
         *
         * We would expect the result to be a graph with:
         * - vertices { ("1", 'A'), ("10", 'C'), ("2", 'B'), ("20", 'D') } and
         * - edges { (("1", "2"), 0.2), (("1", "20"), 0.2), (("10", "2"), 0.2), (("10", "20"), 0.2)
         * }
         */
        val updatedEdges: PersistentMap<Pair<P1, P1>, E> =
            container
                .narrowed()
                .edgesByPointPair
                .entries
                .parallelStream()
                .flatMap { (ek: Pair<P, P>, e: E) ->
                    when (val v1: V? = container.narrowed().verticesByPoint[ek.first]) {
                        null -> {
                            Stream.empty()
                        }
                        else -> {
                            when (val v2: V? = container.narrowed().verticesByPoint[ek.second]) {
                                null -> {
                                    Stream.empty()
                                }
                                else -> {
                                    val newSecondVertexMappings: M = function(ek.second, v2)
                                    function(ek.first, v1).entries.parallelStream().flatMap {
                                        (newFirstPoint: P1, _: V1) ->
                                        newSecondVertexMappings.entries.parallelStream().map {
                                            (newSecondPoint: P1, _: V1) ->
                                            (newFirstPoint to newSecondPoint) to e
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                .reducePairsToPersistentMap()
        return fromVerticesAndEdges(updatedVertices, updatedEdges)
    }

    override fun <P, V, E, E1, M : Map<out Pair<P, P>, E1>> flatMapEdges(
        function: (Pair<P, P>, E) -> M,
        container: PersistentGraphContainer<DirectedGraphWT, P, V, E>
    ): PersistentGraphContainer<DirectedGraphWT, P, V, E1> {
        val vertices: PersistentMap<P, V> = container.narrowed().verticesByPoint
        val updatedEdges: PersistentMap<Pair<P, P>, E1> =
            container
                .narrowed()
                .edgesByPointPair
                .entries
                .parallelStream()
                .flatMap { (ek, e): Map.Entry<Pair<P, P>, E> -> function(ek, e).entries.stream() }
                .filter { (ek, _): Map.Entry<Pair<P, P>, E1> ->
                    ek.first in vertices && ek.second in vertices
                }
                .reduceEntriesToPersistentMap()
        return fromVerticesAndEdges(vertices, updatedEdges)
    }
}
