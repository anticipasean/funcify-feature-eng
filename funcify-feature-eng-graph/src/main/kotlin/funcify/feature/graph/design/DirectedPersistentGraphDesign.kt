package funcify.feature.graph.design

import funcify.feature.graph.DirectedPersistentGraph
import funcify.feature.graph.data.DirectedGraphData
import funcify.feature.graph.data.GraphData
import funcify.feature.graph.data.ParallelizableEdgeDirectedGraphData
import funcify.feature.graph.behavior.GraphBehavior
import java.util.stream.Stream
import kotlinx.collections.immutable.persistentSetOf

/**
 *
 * @author smccarron
 * @created 2023-01-01
 */
internal interface DirectedPersistentGraphDesign<CWT, P, V, E> :
    PersistentGraphDesign<CWT, P, V, E>, DirectedPersistentGraph<P, V, E> {

    override val behavior: GraphBehavior<CWT>

    override val data: GraphData<CWT, P, V, E>

    override fun hasCycles(): Boolean {
        return when (
            val container: GraphData<CWT, P, V, E> = data
        ) {
            is ParallelizableEdgeDirectedGraphData ->
                container.edgesSetByPointPair.keys
                    .parallelStream()
                    .map { pathPair: Pair<P, P> -> pathPair.second to pathPair.first }
                    .anyMatch { pathPair -> pathPair in container.edgesSetByPointPair }
            is DirectedGraphData ->
                container.edgesByPointPair.keys
                    .parallelStream()
                    .map { pathPair: Pair<P, P> -> pathPair.second to pathPair.first }
                    .anyMatch { pathPair -> pathPair in container.edgesByPointPair }
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
        return when (
            val container: GraphData<CWT, P, V, E> = data
        ) {
            is ParallelizableEdgeDirectedGraphData ->
                container.edgesSetByPointPair.keys.parallelStream().flatMap { pathPair: Pair<P, P>
                    ->
                    val reversedPair = pathPair.second to pathPair.first
                    container.edgesSetByPointPair
                        .getOrElse(pathPair) { -> persistentSetOf() }
                        .stream()
                        .flatMap { e1: E ->
                            container.edgesSetByPointPair
                                .getOrElse(reversedPair) { -> persistentSetOf() }
                                .stream()
                                .map { e2: E ->
                                    Triple(pathPair.first, pathPair.second, e1) to
                                        Triple(reversedPair.first, reversedPair.second, e2)
                                }
                        }
                }
            is DirectedGraphData ->
                container.edgesByPointPair.keys.parallelStream().flatMap { pathPair: Pair<P, P> ->
                    val reversedPair = pathPair.second to pathPair.first
                    val e1: E = container.edgesByPointPair[pathPair]!!
                    when (val e2: E? = container.edgesByPointPair[reversedPair]) {
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

    override fun successorVertices(point: P): Iterable<Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun successorVerticesAsStream(point: P): Stream<out Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun successorVertices(vertex: V, pointExtractor: (V) -> P): Iterable<Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun successorVerticesAsStream(
        vertex: V,
        pointExtractor: (V) -> P
    ): Stream<out Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun predecessorVertices(point: P): Iterable<Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun predecessorVerticesAsStream(point: P): Stream<out Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun predecessorVertices(vertex: V, pointExtractor: (V) -> P): Iterable<Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun predecessorVerticesAsStream(
        vertex: V,
        pointExtractor: (V) -> P
    ): Stream<out Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun adjacentVertices(point: P): Iterable<Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun adjacentVerticesAsStream(point: P): Stream<out Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun adjacentVertices(vertex: V, pointExtractor: (V) -> P): Iterable<Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun adjacentVerticesAsStream(
        vertex: V,
        pointExtractor: (V) -> P
    ): Stream<out Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun edgesFromPoint(point: P): Iterable<E> {
        TODO("Not yet implemented")
    }

    override fun edgesFromPointAsStream(point: P): Stream<out E> {
        TODO("Not yet implemented")
    }

    override fun edgesToPoint(point: P): Iterable<E> {
        TODO("Not yet implemented")
    }

    override fun edgesToPointAsStream(point: P): Stream<out E> {
        TODO("Not yet implemented")
    }
}
