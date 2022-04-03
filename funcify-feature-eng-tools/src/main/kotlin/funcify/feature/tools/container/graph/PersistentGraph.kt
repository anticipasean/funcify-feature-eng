package funcify.feature.tools.container.graph

import arrow.core.Option
import arrow.core.Tuple5
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import java.util.Spliterator
import java.util.Spliterators
import java.util.stream.Stream


/**
 *
 * @author smccarron
 * @created 2/7/22
 */
interface PersistentGraph<P, V, E> : Iterable<Tuple5<V, P, E, P, V>> {

    companion object {

        fun <P, V, E> empty(): PersistentGraph<P, V, E> {
            return PersistentGraphFactory.PathBasedGraph(persistentMapOf(),
                                                         persistentMapOf())
        }

        fun <P, V, E> of(path: P,
                         vertex: V): PersistentGraph<P, V, E> {
            return PersistentGraphFactory.PathBasedGraph(persistentMapOf(Pair(path,
                                                                              vertex)),
                                                         persistentMapOf());
        }

        fun <P, V, E> of(vertex1: V,
                         path1: P,
                         edge: E,
                         path2: P,
                         vertex2: V): PersistentGraph<P, V, E> {
            return PersistentGraphFactory.PathBasedGraph(persistentMapOf(Pair(path1,
                                                                              vertex1),
                                                                         Pair(path2,
                                                                              vertex2)),
                                                         persistentMapOf(Pair(Pair(path1,
                                                                                   path2),
                                                                              edge)));
        }

    }

    fun verticesByPath(): PersistentMap<P, V>

    fun edgesByPathPair(): PersistentMap<Pair<P, P>, E>

    fun vertices(): Stream<V> {
        return verticesByPath().values.stream()
    }

    fun edges(): Stream<E> {
        return edgesByPathPair().values.stream()
    }

    fun connectedPaths(): Stream<Pair<P, P>> {
        return edgesByPathPair().keys.stream()
    }

    /**
     * Each tuple containing one full connection within the graph:
     * - vertex 1 -> vertex 1's path -> the edge -> vertex 2's path -> vertex 2
     *
     * This iterator will not include _disconnected_ vertices, those without an edge
     * connecting it to another vertex
     */
    override fun iterator(): Iterator<Tuple5<V, P, E, P, V>> {
        return Spliterators.iterator(spliterator())
    }

    override fun spliterator(): Spliterator<Tuple5<V, P, E, P, V>> {
        return connectedPaths().map({ (p1: P, p2: P) ->
                                        getVertex(p1).zip(getEdgeFromPathToPath(p1,
                                                                                p2),
                                                          getVertex(p2)) { v1: V, e: E, v2: V ->
                                            Tuple5(v1,
                                                   p1,
                                                   e,
                                                   p2,
                                                   v2)
                                        }
                                    })
                .filter { tupleOpt: Option<Tuple5<V, P, E, P, V>> -> tupleOpt.isDefined() }
                .map { tupleOpt: Option<Tuple5<V, P, E, P, V>> -> tupleOpt.orNull()!! }
                .spliterator()

    }

    fun getVertex(path: P): Option<V>

    fun putVertex(path: P,
                  vertex: V): PersistentGraph<P, V, E>

    fun putVertex(vertex: V,
                  pathExtractor: (V) -> P): PersistentGraph<P, V, E> {
        return putVertex(pathExtractor.invoke(vertex),
                         vertex)
    }

    fun putEdge(path1: P,
                path2: P,
                edge: E): PersistentGraph<P, V, E>

    fun putEdge(connectedPaths: Pair<P, P>,
                edge: E): PersistentGraph<P, V, E>

    fun putEdge(edge: E,
                pathsExtractor: (E) -> Pair<P, P>): PersistentGraph<P, V, E> {
        return pathsExtractor.invoke(edge)
                .let { pair ->
                    putEdge(pair.first,
                            pair.second,
                            edge)
                }
    }

    fun getEdgeFromPathToPath(path1: P,
                              path2: P): Option<E>

    fun getEdgesFrom(path: P): Stream<E> {
        return connectedPaths().filter { pair: Pair<P, P> -> pair.first == path }
                .map { pair: Pair<P, P> ->
                    getEdgeFromPathToPath(path1 = pair.first,
                                          path2 = pair.second)
                }
                .flatMap { edgeOption: Option<E> -> edgeOption.fold({ Stream.empty() }) { e: E -> Stream.of(e) } }
    }

    fun getEdgesTo(path: P): Stream<E> {
        return connectedPaths().filter { pair: Pair<P, P> -> pair.second == path }
                .map { pair: Pair<P, P> ->
                    getEdgeFromPathToPath(path1 = pair.first,
                                          path2 = pair.second)
                }
                .flatMap { edgeOption: Option<E> -> edgeOption.fold({ Stream.empty() }) { e: E -> Stream.of(e) } }
    }

    fun getFullConnectionFromPathToPath(path1: P,
                                        path2: P): Option<Tuple5<V, P, E, P, V>> {
        return getVertex(path1).zip(getEdgeFromPathToPath(path1,
                                                          path2),
                                    getVertex(path2)) { v1: V, e: E, v2: V ->
            Tuple5(v1,
                   path1,
                   e,
                   path2,
                   v2)
        }
    }

    fun filterVertices(function: (V) -> Boolean): PersistentGraph<P, V, E>

    fun filterEdges(function: (E) -> Boolean): PersistentGraph<P, V, E>

    fun <R> mapVertices(function: (V) -> R): PersistentGraph<P, R, E>

    fun <R> mapEdges(function: (E) -> R): PersistentGraph<P, V, R>

    fun <R> flatMapVertices(function: (V) -> PersistentGraph<P, R, E>): PersistentGraph<P, R, E>

    fun <R> flatMapEdges(function: (E) -> PersistentGraph<P, V, R>): PersistentGraph<P, V, R>

    fun hasCycles(): Boolean

    fun getCycles(): Sequence<Pair<Triple<P, P, E>, Triple<P, P, E>>>

    fun createMinimumSpanningTreeGraphUsingEdgeCostFunction(costComparator: Comparator<E>): PersistentGraph<P, V, E>

    fun depthFirstSearchOnPath(path: P): Stream<Tuple5<V, P, E, P, V>>

}