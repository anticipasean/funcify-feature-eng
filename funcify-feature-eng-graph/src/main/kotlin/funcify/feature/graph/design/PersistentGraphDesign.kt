package funcify.feature.graph.design

import funcify.feature.graph.PersistentGraph
import funcify.feature.graph.container.PersistentGraphContainer
import funcify.feature.graph.container.PersistentGraphContainerFactory
import funcify.feature.graph.source.PersistentGraphSourceContextFactory
import funcify.feature.graph.template.PersistentGraphTemplate
import java.util.logging.Logger
import java.util.stream.Stream
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

    override fun get(path: P): V? {
        return when (
            val container: PersistentGraphContainer<CWT, P, V, E> = materializedContainer
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                container.verticesByPath[path]
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
                container.verticesByPath[path]
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun get(path1: P, path2: P): Iterable<E> {
        return get(path1 to path2)
    }

    override fun get(pathPair: Pair<P, P>): Iterable<E> {
        return when (
            val container: PersistentGraphContainer<CWT, P, V, E> = this.materializedContainer
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                container.edgesSetByPathPair[pathPair] ?: persistentSetOf<E>()
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
                container.edgesByPathPair[pathPair]?.let { persistentSetOf(it) }
                    ?: persistentSetOf<E>()
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun put(path: P, vertex: V): PersistentGraph<P, V, E> {
        logger.info("put: { path: $path, vertex: $vertex }")
        return when (
            val container: PersistentGraphContainer<CWT, P, V, E> =
                this.template.put(path, vertex, materializedContainer)
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

    override fun put(path1: P, path2: P, edge: E): PersistentGraph<P, V, E> {
        logger.info("put: { path1: $path1, path2: $path2, edge: $edge }")
        return when (
            val container: PersistentGraphContainer<CWT, P, V, E> =
                this.template.put(path1, path2, edge, materializedContainer)
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

    override fun put(pathPair: Pair<P, P>, edge: E): PersistentGraph<P, V, E> {
        logger.info("put: { pathPair: $pathPair, edge: $edge }")
        return when (
            val container: PersistentGraphContainer<CWT, P, V, E> =
                this.template.put(pathPair, edge, materializedContainer)
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

    override fun filterVertices(condition: (V) -> Boolean): PersistentGraph<P, V, E> {
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

    override fun filterEdges(condition: (E) -> Boolean): PersistentGraph<P, V, E> {
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

    override fun <R> mapVertices(function: (V) -> R): PersistentGraph<P, R, E> {
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

    override fun <R> mapEdges(function: (E) -> R): PersistentGraph<P, V, R> {
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
                container.verticesByPath.size
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
                container.verticesByPath.size
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
                container.edgesSetByPathPair.values
                    .stream()
                    .mapToInt { set: PersistentSet<E> -> set.size }
                    .sum()
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
                container.edgesByPathPair.size
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
                container.verticesByPath.values
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
                container.verticesByPath.values
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
                container.verticesByPath.values.stream()
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
                container.verticesByPath.values.stream()
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
                container.edgesByPathPair.values
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
                container.edgesSetByPathPair.values.stream().flatMap { s: PersistentSet<E> ->
                    s.stream()
                }
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
                container.edgesByPathPair.values.stream()
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun connectedPaths(): Iterable<Pair<P, P>> {
        return when (
            val container: PersistentGraphContainer<CWT, P, V, E> = this.materializedContainer
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                container.edgesSetByPathPair.keys
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
                container.edgesByPathPair.keys
            }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun connectedPathsAsStream(): Stream<out Pair<P, P>> {
        return when (
            val container: PersistentGraphContainer<CWT, P, V, E> = this.materializedContainer
        ) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph -> {
                container.edgesSetByPathPair.keys.stream()
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
                container.edgesByPathPair.keys.stream()
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
                container.verticesByPath
                    .asSequence()
                    .map { (p, v) -> p to v }
                    .fold(initial, accumulator)
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
                container.verticesByPath
                    .asSequence()
                    .map { (k, v) -> k to v }
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
                container.edgesSetByPathPair
                    .asSequence()
                    .flatMap { (k, v) -> v.asSequence().map { e -> k to e } }
                    .fold(initial, accumulator)
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
                container.edgesByPathPair
                    .asSequence()
                    .map { (k, v) -> k to v }
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
                container.verticesByPath.keys
                    .reversed()
                    .asSequence()
                    .map { p -> p to container.verticesByPath[p]!! }
                    .fold(initial) { result, entry -> accumulator(entry, result) }
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
                container.verticesByPath.keys
                    .reversed()
                    .asSequence()
                    .map { p -> p to container.verticesByPath[p]!! }
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
                container.edgesSetByPathPair.keys
                    .reversed()
                    .asSequence()
                    .map { ek -> ek to container.edgesSetByPathPair[ek]!! }
                    .flatMap { (ek, eSet) -> eSet.asSequence().map { e -> ek to e } }
                    .fold(initial) { result, entry -> accumulator(entry, result) }
            }
            is PersistentGraphContainerFactory.DirectedGraph -> {
                container.edgesByPathPair.keys
                    .reversed()
                    .asSequence()
                    .map { ek -> ek to container.edgesByPathPair[ek]!! }
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
