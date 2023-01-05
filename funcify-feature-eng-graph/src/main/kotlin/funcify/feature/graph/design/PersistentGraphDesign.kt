package funcify.feature.graph.design

import funcify.feature.graph.GraphDescriptor
import funcify.feature.graph.PersistentGraph
import funcify.feature.graph.behavior.GraphBehavior
import funcify.feature.graph.context.DirectedPersistentGraphContext
import funcify.feature.graph.context.ParallelizableEdgeGraphContext
import funcify.feature.graph.data.DirectedGraphData
import funcify.feature.graph.data.GraphData
import funcify.feature.graph.data.ParallelizableEdgeDirectedGraphData
import java.util.logging.Logger
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf

/**
 * The **design** of a graph includes both its data/contents [GraphData] and its behavior
 * [GraphBehavior]
 *
 * Implementations of a graph **design** are **contexts** e.g. [DirectedPersistentGraphContext]
 */
internal interface PersistentGraphDesign<CWT, P, V, E> : PersistentGraph<P, V, E> {

    companion object {
        private val logger: Logger = Logger.getLogger(PersistentGraphDesign::class.simpleName)
    }

    val behavior: GraphBehavior<CWT>

    val data: GraphData<CWT, P, V, E>

    override fun get(point: P): V? {
        return when (val container: GraphData<CWT, P, V, E> = data) {
            is ParallelizableEdgeDirectedGraphData -> {
                container.verticesByPoint[point]
            }
            is DirectedGraphData -> {
                container.verticesByPoint[point]
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun get(point1: P, point2: P): Iterable<E> {
        return get(point1 to point2)
    }

    override fun get(pointPair: Pair<P, P>): Iterable<E> {
        return when (val container: GraphData<CWT, P, V, E> = this.data) {
            is ParallelizableEdgeDirectedGraphData -> {
                container.edgesSetByPointPair[pointPair] ?: persistentSetOf<E>()
            }
            is DirectedGraphData -> {
                container.edgesByPointPair[pointPair]?.let { persistentSetOf(it) }
                    ?: persistentSetOf<E>()
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun put(point: P, vertex: V): PersistentGraph<P, V, E> {
        return when (
            val container: GraphData<CWT, P, V, E> = this.behavior.put(point, vertex, data)
        ) {
            is ParallelizableEdgeDirectedGraphData -> {
                ParallelizableEdgeGraphContext<P, V, E>(data = container)
            }
            is DirectedGraphData -> {
                DirectedPersistentGraphContext<P, V, E>(data = container)
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun put(point1: P, point2: P, edge: E): PersistentGraph<P, V, E> {
        logger.info("put: { path1: $point1, path2: $point2, edge: $edge }")
        return when (
            val container: GraphData<CWT, P, V, E> = this.behavior.put(point1, point2, edge, data)
        ) {
            is ParallelizableEdgeDirectedGraphData -> {
                ParallelizableEdgeGraphContext<P, V, E>(data = container)
            }
            is DirectedGraphData -> {
                DirectedPersistentGraphContext<P, V, E>(data = container)
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun put(pointPair: Pair<P, P>, edge: E): PersistentGraph<P, V, E> {
        logger.info("put: { pathPair: $pointPair, edge: $edge }")
        return when (
            val container: GraphData<CWT, P, V, E> = this.behavior.put(pointPair, edge, data)
        ) {
            is ParallelizableEdgeDirectedGraphData -> {
                ParallelizableEdgeGraphContext<P, V, E>(data = container)
            }
            is DirectedGraphData -> {
                DirectedPersistentGraphContext<P, V, E>(data = container)
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun <M : Map<P, V>> putAllVertices(vertices: M): PersistentGraph<P, V, E> {
        return when (
            val container: GraphData<CWT, P, V, E> = this.behavior.putAllVertices(vertices, data)
        ) {
            is ParallelizableEdgeDirectedGraphData -> {
                ParallelizableEdgeGraphContext<P, V, E>(data = container)
            }
            is DirectedGraphData -> {
                DirectedPersistentGraphContext<P, V, E>(data = container)
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun <M : Map<Pair<P, P>, E>> putAllEdges(edges: M): PersistentGraph<P, V, E> {
        return when (
            val container: GraphData<CWT, P, V, E> = this.behavior.putAllEdges(edges, data)
        ) {
            is ParallelizableEdgeDirectedGraphData -> {
                ParallelizableEdgeGraphContext<P, V, E>(data = container)
            }
            is DirectedGraphData -> {
                DirectedPersistentGraphContext<P, V, E>(data = container)
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun <S : Set<E>, M : Map<Pair<P, P>, S>> putAllEdgeSets(
        edges: M
    ): PersistentGraph<P, V, E> {
        return when (
            val container: GraphData<CWT, P, V, E> = this.behavior.putAllEdgeSets(edges, data)
        ) {
            is ParallelizableEdgeDirectedGraphData -> {
                ParallelizableEdgeGraphContext<P, V, E>(data = container)
            }
            is DirectedGraphData -> {
                DirectedPersistentGraphContext<P, V, E>(data = container)
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun remove(point: P): PersistentGraph<P, V, E> {
        return when (val container: GraphData<CWT, P, V, E> = this.behavior.remove(point, data)) {
            is ParallelizableEdgeDirectedGraphData -> {
                ParallelizableEdgeGraphContext<P, V, E>(data = container)
            }
            is DirectedGraphData -> {
                DirectedPersistentGraphContext<P, V, E>(data = container)
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun descriptors(): ImmutableSet<GraphDescriptor> {
        return when (val container: GraphData<CWT, P, V, E> = data) {
            is ParallelizableEdgeDirectedGraphData -> {
                persistentSetOf<GraphDescriptor>(
                    GraphDescriptor.DIRECTED,
                    GraphDescriptor.CONTAINS_PARALLEL_EDGES
                )
            }
            is DirectedGraphData -> {
                persistentSetOf<GraphDescriptor>(GraphDescriptor.DIRECTED)
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun filterVertices(condition: (P, V) -> Boolean): PersistentGraph<P, V, E> {
        return when (
            val container: GraphData<CWT, P, V, E> = this.behavior.filterVertices(condition, data)
        ) {
            is ParallelizableEdgeDirectedGraphData -> {
                ParallelizableEdgeGraphContext<P, V, E>(data = container)
            }
            is DirectedGraphData -> {
                DirectedPersistentGraphContext<P, V, E>(data = container)
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun filterEdges(condition: (Pair<P, P>, E) -> Boolean): PersistentGraph<P, V, E> {
        return when (
            val container: GraphData<CWT, P, V, E> = this.behavior.filterEdges(condition, data)
        ) {
            is ParallelizableEdgeDirectedGraphData -> {
                ParallelizableEdgeGraphContext<P, V, E>(data = container)
            }
            is DirectedGraphData -> {
                DirectedPersistentGraphContext<P, V, E>(data = container)
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun <R> mapPoints(function: (P, V) -> R): PersistentGraph<R, V, E> {
        return when (
            val container: GraphData<CWT, R, V, E> = this.behavior.mapPoints(function, data)
        ) {
            is ParallelizableEdgeDirectedGraphData -> {
                ParallelizableEdgeGraphContext<R, V, E>(data = container)
            }
            is DirectedGraphData -> {
                DirectedPersistentGraphContext<R, V, E>(data = container)
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun <R> mapPoints(function: (P) -> R): PersistentGraph<R, V, E> {
        return when (
            val container: GraphData<CWT, R, V, E> = this.behavior.mapPoints(function, data)
        ) {
            is ParallelizableEdgeDirectedGraphData -> {
                ParallelizableEdgeGraphContext<R, V, E>(data = container)
            }
            is DirectedGraphData -> {
                DirectedPersistentGraphContext<R, V, E>(data = container)
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun <R> mapVertices(function: (P, V) -> R): PersistentGraph<P, R, E> {
        return when (
            val container: GraphData<CWT, P, R, E> = this.behavior.mapVertices(function, data)
        ) {
            is ParallelizableEdgeDirectedGraphData -> {
                ParallelizableEdgeGraphContext<P, R, E>(data = container)
            }
            is DirectedGraphData -> {
                DirectedPersistentGraphContext<P, R, E>(data = container)
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun <R> mapEdges(function: (Pair<P, P>, E) -> R): PersistentGraph<P, V, R> {
        return when (
            val container: GraphData<CWT, P, V, R> = this.behavior.mapEdges(function, data)
        ) {
            is ParallelizableEdgeDirectedGraphData -> {
                ParallelizableEdgeGraphContext<P, V, R>(data = container)
            }
            is DirectedGraphData -> {
                DirectedPersistentGraphContext<P, V, R>(data = container)
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun <P1, V1, M : Map<out P1, V1>> flatMapVertices(
        function: (P, V) -> M
    ): PersistentGraph<P1, V1, E> {
        return when (
            val container: GraphData<CWT, P1, V1, E> = this.behavior.flatMapVertices(function, data)
        ) {
            is ParallelizableEdgeDirectedGraphData -> {
                ParallelizableEdgeGraphContext<P1, V1, E>(data = container)
            }
            is DirectedGraphData -> {
                DirectedPersistentGraphContext<P1, V1, E>(data = container)
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun <E1, M : Map<out Pair<P, P>, E1>> flatMapEdges(
        function: (Pair<P, P>, E) -> M
    ): PersistentGraph<P, V, E1> {
        return when (
            val container: GraphData<CWT, P, V, E1> = this.behavior.flatMapEdges(function, data)
        ) {
            is ParallelizableEdgeDirectedGraphData -> {
                ParallelizableEdgeGraphContext<P, V, E1>(data = container)
            }
            is DirectedGraphData -> {
                DirectedPersistentGraphContext<P, V, E1>(data = container)
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun vertexCount(): Int {
        return when (val container: GraphData<CWT, P, V, E> = this.data) {
            is ParallelizableEdgeDirectedGraphData -> {
                container.verticesByPoint.size
            }
            is DirectedGraphData -> {
                container.verticesByPoint.size
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun edgeCount(): Int {
        return when (val container: GraphData<CWT, P, V, E> = this.data) {
            is ParallelizableEdgeDirectedGraphData -> {
                container.edgesSetByPointPair.values
                    .stream()
                    .mapToInt { set: PersistentSet<E> -> set.size }
                    .sum()
            }
            is DirectedGraphData -> {
                container.edgesByPointPair.size
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun vertices(): Iterable<V> {
        return when (val container: GraphData<CWT, P, V, E> = this.data) {
            is ParallelizableEdgeDirectedGraphData -> {
                container.verticesByPoint.values
            }
            is DirectedGraphData -> {
                container.verticesByPoint.values
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun verticesAsStream(): Stream<out V> {
        return when (val container: GraphData<CWT, P, V, E> = this.data) {
            is ParallelizableEdgeDirectedGraphData -> {
                container.verticesByPoint.values.stream()
            }
            is DirectedGraphData -> {
                container.verticesByPoint.values.stream()
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun edges(): Iterable<E> {
        return when (val container: GraphData<CWT, P, V, E> = this.data) {
            is ParallelizableEdgeDirectedGraphData -> {
                Iterable<E> { edgesAsStream().iterator() }
            }
            is DirectedGraphData -> {
                container.edgesByPointPair.values
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun edgesAsStream(): Stream<out E> {
        return when (val container: GraphData<CWT, P, V, E> = this.data) {
            is ParallelizableEdgeDirectedGraphData -> {
                container.edgesSetByPointPair.values.stream().flatMap { s: PersistentSet<E> ->
                    s.stream()
                }
            }
            is DirectedGraphData -> {
                container.edgesByPointPair.values.stream()
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun connectedPoints(): Iterable<Pair<P, P>> {
        return when (val container: GraphData<CWT, P, V, E> = this.data) {
            is ParallelizableEdgeDirectedGraphData -> {
                container.edgesSetByPointPair.keys
            }
            is DirectedGraphData -> {
                container.edgesByPointPair.keys
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun connectedPointsAsStream(): Stream<out Pair<P, P>> {
        return when (val container: GraphData<CWT, P, V, E> = this.data) {
            is ParallelizableEdgeDirectedGraphData -> {
                container.edgesSetByPointPair.keys.stream()
            }
            is DirectedGraphData -> {
                container.edgesByPointPair.keys.stream()
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun <R> foldLeftVertices(initial: R, accumulator: (R, Pair<P, V>) -> R): R {
        return when (val container: GraphData<CWT, P, V, E> = this.data) {
            is ParallelizableEdgeDirectedGraphData -> {
                container.verticesByPoint
                    .asSequence()
                    .map { (p, v) -> p to v }
                    .fold(initial, accumulator)
            }
            is DirectedGraphData -> {
                container.verticesByPoint
                    .asSequence()
                    .map { (p, v) -> p to v }
                    .fold(initial, accumulator)
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun <R> foldLeftEdges(initial: R, accumulator: (R, Pair<Pair<P, P>, E>) -> R): R {
        return when (val container: GraphData<CWT, P, V, E> = this.data) {
            is ParallelizableEdgeDirectedGraphData -> {
                container.edgesSetByPointPair
                    .asSequence()
                    .flatMap { (ek, edges) -> edges.asSequence().map { e -> ek to e } }
                    .fold(initial, accumulator)
            }
            is DirectedGraphData -> {
                container.edgesByPointPair
                    .asSequence()
                    .map { (ek, e) -> ek to e }
                    .fold(initial, accumulator)
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun <R> foldRightVertices(initial: R, accumulator: (Pair<P, V>, R) -> R): R {
        return when (val container: GraphData<CWT, P, V, E> = this.data) {
            is ParallelizableEdgeDirectedGraphData -> {
                container.verticesByPoint.keys
                    .reversed()
                    .asSequence()
                    .map { p -> p to container.verticesByPoint[p]!! }
                    .fold(initial) { result, entry -> accumulator(entry, result) }
            }
            is DirectedGraphData -> {
                container.verticesByPoint.keys
                    .reversed()
                    .asSequence()
                    .map { p -> p to container.verticesByPoint[p]!! }
                    .fold(initial) { result, entry -> accumulator(entry, result) }
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun <R> foldRightEdges(initial: R, accumulator: (Pair<Pair<P, P>, E>, R) -> R): R {
        return when (val container: GraphData<CWT, P, V, E> = this.data) {
            is ParallelizableEdgeDirectedGraphData -> {
                container.edgesSetByPointPair.keys
                    .reversed()
                    .asSequence()
                    .map { ek -> ek to container.edgesSetByPointPair[ek]!! }
                    .flatMap { (ek, eSet) -> eSet.asSequence().map { e -> ek to e } }
                    .fold(initial) { result, entry -> accumulator(entry, result) }
            }
            is DirectedGraphData -> {
                container.edgesByPointPair.keys
                    .reversed()
                    .asSequence()
                    .map { ek -> ek to container.edgesByPointPair[ek]!! }
                    .fold(initial) { result, entry -> accumulator(entry, result) }
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun stringify(
        pointStringifier: (P) -> String,
        vertexStringifier: (V) -> String,
        edgeStringifier: (E) -> String
    ): String {
        val pathStringifier: (Pair<P, P>) -> String = { (p1, p2) ->
            """"path":{"source":${pointStringifier(p1)},"destination":${pointStringifier(p2)}}"""
        }
        val vertexByPointStringifier: (P, V) -> String = { p, v ->
            """{"point":${pointStringifier(p)},"vertex":${vertexStringifier(v)}}"""
        }
        val edgeByPathStringifier: (Pair<P, P>, E) -> String = { ek, e ->
            """{${pathStringifier(ek)},"edge":${edgeStringifier(e)}}"""
        }
        return when (val container: GraphData<CWT, P, V, E> = this.data) {
            is ParallelizableEdgeDirectedGraphData -> {
                StringBuilder("{")
                    .append(""""vertices":[""")
                    .append(
                        container.verticesByPoint.entries
                            .stream()
                            .map { (p, v) -> vertexByPointStringifier(p, v) }
                            .collect(Collectors.joining(","))
                    )
                    .append("]")
                    .append(",")
                    .append(""""edges":[""")
                    .append(
                        container.edgesSetByPointPair.entries
                            .stream()
                            .flatMap { (ek, edges) ->
                                edges.stream().map { e -> edgeByPathStringifier(ek, e) }
                            }
                            .collect(Collectors.joining(","))
                    )
                    .append("]")
                    .append("}")
                    .toString()
            }
            is DirectedGraphData -> {
                StringBuilder("{")
                    .append(""""vertices":[""")
                    .append(
                        container.verticesByPoint.entries
                            .stream()
                            .map { (p, v) -> vertexByPointStringifier(p, v) }
                            .collect(Collectors.joining(","))
                    )
                    .append("]")
                    .append(",")
                    .append(""""edges":[""")
                    .append(
                        container.edgesByPointPair.entries
                            .stream()
                            .map { (ek, e) -> edgeByPathStringifier(ek, e) }
                            .collect(Collectors.joining(","))
                    )
                    .append("]")
                    .append("}")
                    .toString()
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }
}
