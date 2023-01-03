package funcify.feature.graph.design

import funcify.feature.graph.GraphDescriptor
import funcify.feature.graph.PersistentGraph
import funcify.feature.graph.container.PersistentGraphContainer
import funcify.feature.graph.container.PersistentGraphContainerFactory
import funcify.feature.graph.source.PersistentGraphSourceContextFactory
import funcify.feature.graph.template.PersistentGraphTemplate
import java.util.logging.Logger
import java.util.stream.Stream
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf

/**
 * Design of a graph includes both its contents (=> graph_container) and its behavior (=>
 * graph_template)
 */
internal interface PersistentGraphDesign<CWT, P, V, E> : PersistentGraph<P, V, E> {

    companion object {
        private val logger: Logger = Logger.getLogger(PersistentGraphDesign::class.simpleName)
    }

    val template: PersistentGraphTemplate<CWT>

    val materializedContainer: PersistentGraphContainer<CWT, P, V, E>

    override fun get(point: P): V? {
        return when (
            val container: PersistentGraphContainer<CWT, P, V, E> = materializedContainer
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                container.verticesByPoint[point]
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
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
        return when (
            val container: PersistentGraphContainer<CWT, P, V, E> = this.materializedContainer
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                container.edgesSetByPointPair[pointPair] ?: persistentSetOf<E>()
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
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
            val container: PersistentGraphContainer<CWT, P, V, E> =
                this.template.put(point, vertex, materializedContainer)
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                PersistentGraphSourceContextFactory.ParallelizableEdgeGraphSourceDesign<P, V, E>(
                    materializedContainer = container
                )
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
                PersistentGraphSourceContextFactory.DirectedPersistentGraphSourceDesign<P, V, E>(
                    materializedContainer = container
                )
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
            val container: PersistentGraphContainer<CWT, P, V, E> =
                this.template.put(point1, point2, edge, materializedContainer)
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                PersistentGraphSourceContextFactory.ParallelizableEdgeGraphSourceDesign<P, V, E>(
                    materializedContainer = container
                )
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
                PersistentGraphSourceContextFactory.DirectedPersistentGraphSourceDesign<P, V, E>(
                    materializedContainer = container
                )
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
            val container: PersistentGraphContainer<CWT, P, V, E> =
                this.template.put(pointPair, edge, materializedContainer)
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                PersistentGraphSourceContextFactory.ParallelizableEdgeGraphSourceDesign<P, V, E>(
                    materializedContainer = container
                )
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
                PersistentGraphSourceContextFactory.DirectedPersistentGraphSourceDesign<P, V, E>(
                    materializedContainer = container
                )
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
            val container: PersistentGraphContainer<CWT, P, V, E> =
                this.template.putAllVertices(vertices, materializedContainer)
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                PersistentGraphSourceContextFactory.ParallelizableEdgeGraphSourceDesign<P, V, E>(
                    materializedContainer = container
                )
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
                PersistentGraphSourceContextFactory.DirectedPersistentGraphSourceDesign<P, V, E>(
                    materializedContainer = container
                )
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
            val container: PersistentGraphContainer<CWT, P, V, E> =
                this.template.putAllEdges(edges, materializedContainer)
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                PersistentGraphSourceContextFactory.ParallelizableEdgeGraphSourceDesign<P, V, E>(
                    materializedContainer = container
                )
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
                PersistentGraphSourceContextFactory.DirectedPersistentGraphSourceDesign<P, V, E>(
                    materializedContainer = container
                )
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
            val container: PersistentGraphContainer<CWT, P, V, E> =
                this.template.putAllEdgeSets(edges, materializedContainer)
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                PersistentGraphSourceContextFactory.ParallelizableEdgeGraphSourceDesign<P, V, E>(
                    materializedContainer = container
                )
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
                PersistentGraphSourceContextFactory.DirectedPersistentGraphSourceDesign<P, V, E>(
                    materializedContainer = container
                )
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun descriptors(): ImmutableSet<GraphDescriptor> {
        return when (
            val container: PersistentGraphContainer<CWT, P, V, E> = materializedContainer
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                persistentSetOf<GraphDescriptor>(
                    GraphDescriptor.DIRECTED,
                    GraphDescriptor.CONTAINS_PARALLEL_EDGES
                )
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
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
            val container: PersistentGraphContainer<CWT, P, V, E> =
                this.template.filterVertices(condition, materializedContainer)
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                PersistentGraphSourceContextFactory.ParallelizableEdgeGraphSourceDesign<P, V, E>(
                    materializedContainer = container
                )
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
                PersistentGraphSourceContextFactory.DirectedPersistentGraphSourceDesign<P, V, E>(
                    materializedContainer = container
                )
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
            val container: PersistentGraphContainer<CWT, P, V, E> =
                this.template.filterEdges(condition, materializedContainer)
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                PersistentGraphSourceContextFactory.ParallelizableEdgeGraphSourceDesign<P, V, E>(
                    materializedContainer = container
                )
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
                PersistentGraphSourceContextFactory.DirectedPersistentGraphSourceDesign<P, V, E>(
                    materializedContainer = container
                )
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
            val container: PersistentGraphContainer<CWT, R, V, E> =
                this.template.mapPoints(function, materializedContainer)
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                PersistentGraphSourceContextFactory.ParallelizableEdgeGraphSourceDesign<R, V, E>(
                    materializedContainer = container
                )
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
                PersistentGraphSourceContextFactory.DirectedPersistentGraphSourceDesign<R, V, E>(
                    materializedContainer = container
                )
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
            val container: PersistentGraphContainer<CWT, R, V, E> =
                this.template.mapPoints(function, materializedContainer)
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                PersistentGraphSourceContextFactory.ParallelizableEdgeGraphSourceDesign<R, V, E>(
                    materializedContainer = container
                )
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
                PersistentGraphSourceContextFactory.DirectedPersistentGraphSourceDesign<R, V, E>(
                    materializedContainer = container
                )
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
            val container: PersistentGraphContainer<CWT, P, R, E> =
                this.template.mapVertices(function, materializedContainer)
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                PersistentGraphSourceContextFactory.ParallelizableEdgeGraphSourceDesign<P, R, E>(
                    materializedContainer = container
                )
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
                PersistentGraphSourceContextFactory.DirectedPersistentGraphSourceDesign<P, R, E>(
                    materializedContainer = container
                )
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
            val container: PersistentGraphContainer<CWT, P, V, R> =
                this.template.mapEdges(function, materializedContainer)
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                PersistentGraphSourceContextFactory.ParallelizableEdgeGraphSourceDesign<P, V, R>(
                    materializedContainer = container
                )
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
                PersistentGraphSourceContextFactory.DirectedPersistentGraphSourceDesign<P, V, R>(
                    materializedContainer = container
                )
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun <R, M : Map<out P, R>> flatMapVertices(
        function: (P, V) -> M
    ): PersistentGraph<P, R, E> {
        return when (
            val container: PersistentGraphContainer<CWT, P, R, E> =
                this.template.flatMapVertices(function, materializedContainer)
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                PersistentGraphSourceContextFactory.ParallelizableEdgeGraphSourceDesign<P, R, E>(
                    materializedContainer = container
                )
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
                PersistentGraphSourceContextFactory.DirectedPersistentGraphSourceDesign<P, R, E>(
                    materializedContainer = container
                )
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun <R, M : Map<out Pair<P, P>, R>> flatMapEdges(
        function: (Pair<P, P>, E) -> M
    ): PersistentGraph<P, V, R> {
        return when (
            val container: PersistentGraphContainer<CWT, P, V, R> =
                this.template.flatMapEdges(function, materializedContainer)
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                PersistentGraphSourceContextFactory.ParallelizableEdgeGraphSourceDesign<P, V, R>(
                    materializedContainer = container
                )
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
                PersistentGraphSourceContextFactory.DirectedPersistentGraphSourceDesign<P, V, R>(
                    materializedContainer = container
                )
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun vertexCount(): Int {
        return when (
            val container: PersistentGraphContainer<CWT, P, V, E> = this.materializedContainer
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                container.verticesByPoint.size
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
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
        return when (
            val container: PersistentGraphContainer<CWT, P, V, E> = this.materializedContainer
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                container.edgesSetByPointPair.values
                    .stream()
                    .mapToInt { set: PersistentSet<E> -> set.size }
                    .sum()
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
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
        return when (
            val container: PersistentGraphContainer<CWT, P, V, E> = this.materializedContainer
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                container.verticesByPoint.values
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
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
        return when (
            val container: PersistentGraphContainer<CWT, P, V, E> = this.materializedContainer
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                container.verticesByPoint.values.stream()
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
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
        return when (
            val container: PersistentGraphContainer<CWT, P, V, E> = this.materializedContainer
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                Iterable<E> { edgesAsStream().iterator() }
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
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
        return when (
            val container: PersistentGraphContainer<CWT, P, V, E> = this.materializedContainer
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                container.edgesSetByPointPair.values.stream().flatMap { s: PersistentSet<E> ->
                    s.stream()
                }
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
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
        return when (
            val container: PersistentGraphContainer<CWT, P, V, E> = this.materializedContainer
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                container.edgesSetByPointPair.keys
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
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
        return when (
            val container: PersistentGraphContainer<CWT, P, V, E> = this.materializedContainer
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                container.edgesSetByPointPair.keys.stream()
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
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
        return when (
            val container: PersistentGraphContainer<CWT, P, V, E> = this.materializedContainer
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                container.verticesByPoint
                    .asSequence()
                    .map { (p, v) -> p to v }
                    .fold(initial, accumulator)
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
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
        return when (
            val container: PersistentGraphContainer<CWT, P, V, E> = this.materializedContainer
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                container.edgesSetByPointPair
                    .asSequence()
                    .flatMap { (ek, edges) -> edges.asSequence().map { e -> ek to e } }
                    .fold(initial, accumulator)
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
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
        return when (
            val container: PersistentGraphContainer<CWT, P, V, E> = this.materializedContainer
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                container.verticesByPoint.keys
                    .reversed()
                    .asSequence()
                    .map { p -> p to container.verticesByPoint[p]!! }
                    .fold(initial) { result, entry -> accumulator(entry, result) }
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
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
        return when (
            val container: PersistentGraphContainer<CWT, P, V, E> = this.materializedContainer
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                container.edgesSetByPointPair.keys
                    .reversed()
                    .asSequence()
                    .map { ek -> ek to container.edgesSetByPointPair[ek]!! }
                    .flatMap { (ek, eSet) -> eSet.asSequence().map { e -> ek to e } }
                    .fold(initial) { result, entry -> accumulator(entry, result) }
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
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
}
