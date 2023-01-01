package funcify.feature.graph.design

import funcify.feature.graph.PersistentGraph
import funcify.feature.graph.container.PersistentGraphContainer
import funcify.feature.graph.container.PersistentGraphContainerFactory
import funcify.feature.graph.template.PersistentGraphTemplate
import java.util.logging.Logger
import java.util.stream.Stream
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf

internal abstract class PersistentGraphDesign<CWT, P, V, E>(
    open val template: PersistentGraphTemplate<CWT>,
) : PersistentGraph<P, V, E> {

    companion object {
        private val logger: Logger = Logger.getLogger(PersistentGraphDesign::class.simpleName)
    }

    protected val materializedContainer: PersistentGraphContainer<CWT, P, V, E> by lazy {
        this.fold(template)
    }

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
        return PutVertexDesign<CWT, P, V, E>(
            template = template,
            currentDesign = this,
            path = path,
            newVertex = vertex
        )
    }

    override fun put(path1: P, path2: P, edge: E): PersistentGraph<P, V, E> {
        logger.info("put: { path1: $path1, path2: $path2, edge: $edge }")
        return PutEdgeDesign<CWT, P, V, E>(
            template = template,
            currentDesign = this,
            vertexPath1 = path1,
            vertexPath2 = path2,
            newEdge = edge
        )
    }

    override fun put(pathPair: Pair<P, P>, edge: E): PersistentGraph<P, V, E> {
        logger.info("put: { pathPair: $pathPair, edge: $edge }")
        return PutEdgeDesign<CWT, P, V, E>(
            template = template,
            currentDesign = this,
            vertexPath1 = pathPair.first,
            vertexPath2 = pathPair.second,
            newEdge = edge
        )
    }

    override fun <M : Map<P, V>> putAllVertices(vertices: M): PersistentGraph<P, V, E> {
        return PutVerticesDesign<CWT, P, V, E>(
            template = template,
            currentDesign = this,
            vertices = vertices
        )
    }

    override fun <M : Map<Pair<P, P>, E>> putAllEdges(edges: M): PersistentGraph<P, V, E> {
        return PutEdgesDesign<CWT, P, V, E>(
            template = template,
            currentDesign = this,
            edges = edges
        )
    }

    override fun <S : Set<E>, M : Map<Pair<P, P>, S>> putAllEdgeSets(
        edges: M
    ): PersistentGraph<P, V, E> {
        return PutEdgeSetsDesign<CWT, P, V, E>(
            template = template,
            currentDesign = this,
            edgeSets = edges
        )
    }

    override fun filterVertices(condition: (V) -> Boolean): PersistentGraph<P, V, E> {
        return flatMapVertices { p, v ->
            if (condition(v)) {
                mapOf<P, V>(p to v)
            } else {
                emptyMap<P, V>()
            }
        }
    }

    override fun filterEdges(condition: (E) -> Boolean): PersistentGraph<P, V, E> {
        return flatMapEdges { ek, e ->
            if (condition(e)) {
                mapOf(ek to e)
            } else {
                emptyMap()
            }
        }
    }

    override fun <R> mapVertices(function: (V) -> R): PersistentGraph<P, R, E> {
        return flatMapVertices { p, v -> mapOf(p to function(v)) }
    }

    override fun <R> mapEdges(function: (E) -> R): PersistentGraph<P, V, R> {
        return flatMapEdges { ek, e -> mapOf(ek to function(e)) }
    }

    override fun <R, M : Map<out P, R>> flatMapVertices(
        function: (P, V) -> M
    ): PersistentGraph<P, R, E> {
        return FlatMapVerticesDesign<CWT, P, V, E, R, M>(
            template = template,
            currentDesign = this,
            mapper = function
        )
    }

    override fun <R, M : Map<out Pair<P, P>, R>> flatMapEdges(
        function: (Pair<P, P>, E) -> M
    ): PersistentGraph<P, V, R> {
        return FlatMapEdgesDesign<CWT, P, V, E, R, M>(
            template = template,
            currentDesign = this,
            mapper = function
        )
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

    abstract fun <WT> fold(
        template: PersistentGraphTemplate<WT>
    ): PersistentGraphContainer<WT, P, V, E>
}
