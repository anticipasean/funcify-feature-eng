package funcify.feature.graph.design

import funcify.feature.graph.DirectedPersistentGraph
import funcify.feature.graph.container.PersistentGraphContainer
import funcify.feature.graph.container.PersistentGraphContainerFactory
import java.util.stream.Stream
import kotlinx.collections.immutable.persistentSetOf

/**
 *
 * @author smccarron
 * @created 2023-01-01
 */
internal interface DirectedPersistentGraphDesign<CWT, P, V, E> :
    PersistentGraphDesign<CWT, P, V, E>, DirectedPersistentGraph<P, V, E> {

    override fun hasCycles(): Boolean {
        return when (val container: PersistentGraphContainer<CWT, P, V, E> = this.fold(template)) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph ->
                container.edgesSetByPathPair.keys
                    .parallelStream()
                    .map { pathPair: Pair<P, P> -> pathPair.second to pathPair.first }
                    .anyMatch { pathPair -> pathPair in container.edgesSetByPathPair }
            is PersistentGraphContainerFactory.DirectedGraph ->
                container.edgesByPathPair.keys
                    .parallelStream()
                    .map { pathPair: Pair<P, P> -> pathPair.second to pathPair.first }
                    .anyMatch { pathPair -> pathPair in container.edgesByPathPair }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun getCycles(): Iterable<Pair<Triple<P, P, E>, Triple<P, P, E>>> {
        return Iterable<Pair<Triple<P, P, E>, Triple<P, P, E>>> { getCyclesAsStream().iterator() }
    }

    override fun getCyclesAsStream(): Stream<out Pair<Triple<P, P, E>, Triple<P, P, E>>> {
        return when (val container: PersistentGraphContainer<CWT, P, V, E> = this.fold(template)) {
            is PersistentGraphContainerFactory.ParallelizableEdgeDirectedGraph ->
                container.edgesSetByPathPair.keys.parallelStream().flatMap { pathPair: Pair<P, P> ->
                    val reversedPair = pathPair.second to pathPair.first
                    container.edgesSetByPathPair
                        .getOrElse(pathPair) { -> persistentSetOf() }
                        .stream()
                        .flatMap { e1: E ->
                            container.edgesSetByPathPair
                                .getOrElse(reversedPair) { -> persistentSetOf() }
                                .stream()
                                .map { e2: E ->
                                    Triple(pathPair.first, pathPair.second, e1) to
                                        Triple(reversedPair.first, reversedPair.second, e2)
                                }
                        }
                }
            is PersistentGraphContainerFactory.DirectedGraph ->
                container.edgesByPathPair.keys.parallelStream().flatMap { pathPair: Pair<P, P> ->
                    val reversedPair = pathPair.second to pathPair.first
                    val e1: E = container.edgesByPathPair[pathPair]!!
                    when (val e2: E? = container.edgesByPathPair[reversedPair]) {
                        null -> Stream.empty()
                        else ->
                            Stream.of<Pair<Triple<P, P, E>, Triple<P, P, E>>>(
                                Triple(pathPair.first, pathPair.second, e1) to
                                    Triple(reversedPair.first, reversedPair.second, e2)
                            )
                    }
                }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun successorVertices(vertexPath: P): Iterable<Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun successorVerticesAsStream(vertexPath: P): Stream<out Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun successorVertices(vertex: V, pathExtractor: (V) -> P): Iterable<Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun successorVerticesAsStream(
        vertex: V,
        pathExtractor: (V) -> P
    ): Stream<out Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun predecessorVertices(vertexPath: P): Iterable<Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun predecessorVerticesAsStream(vertexPath: P): Stream<out Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun predecessorVertices(vertex: V, pathExtractor: (V) -> P): Iterable<Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun predecessorVerticesAsStream(
        vertex: V,
        pathExtractor: (V) -> P
    ): Stream<out Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun adjacentVertices(vertexPath: P): Iterable<Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun adjacentVerticesAsStream(vertexPath: P): Stream<out Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun adjacentVertices(vertex: V, pathExtractor: (V) -> P): Iterable<Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun adjacentVerticesAsStream(
        vertex: V,
        pathExtractor: (V) -> P
    ): Stream<out Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun edgesFromPath(path: P): Iterable<E> {
        TODO("Not yet implemented")
    }

    override fun edgesFromPathAsStream(path: P): Stream<out E> {
        TODO("Not yet implemented")
    }

    override fun edgesToPath(path: P): Iterable<E> {
        TODO("Not yet implemented")
    }

    override fun edgesToPathAsStream(path: P): Stream<out E> {
        TODO("Not yet implemented")
    }
}
