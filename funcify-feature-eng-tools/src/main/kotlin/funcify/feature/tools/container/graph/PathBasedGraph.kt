package funcify.feature.tools.container.graph

import arrow.core.Option
import arrow.core.Tuple5
import arrow.core.getOrElse
import arrow.core.some
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import java.util.Spliterator
import java.util.Spliterators
import java.util.stream.Stream


/**
 *
 * @author smccarron
 * @created 4/3/22
 */
sealed interface PathBasedGraph<P, V, E> : PersistentGraph<V, E>,
                                           Iterable<Tuple5<V, P, E, P, V>> {

    companion object {

        fun <P, V, E> emptyTwoPathsToOneEdgeGraph(): PathBasedGraph<P, V, E> {
            return DefaultTwoToOnePathToEdgePathBasedGraph()
        }

        fun <P, V, E> emptyTwoPathsToManyEdgesGraph(): PathBasedGraph<P, V, E> {
            return DefaultTwoToManyEdgePathBasedGraph()
        }

        fun <P, V, E> of(path: P,
                         vertex: V): PathBasedGraph<P, V, E> {
            return DefaultTwoToOnePathToEdgePathBasedGraph(verticesByPath = persistentMapOf(Pair(path,
                                                                                                 vertex)));
        }

        fun <P, V, E> of(vertex1: V,
                         path1: P,
                         edge: E,
                         path2: P,
                         vertex2: V): PathBasedGraph<P, V, E> {
            return DefaultTwoToOnePathToEdgePathBasedGraph(verticesByPath = persistentMapOf(Pair(path1,
                                                                                                 vertex1),
                                                                                            Pair(path2,
                                                                                           vertex2)),
                                                           edgesByPathPair = persistentMapOf(Pair(Pair(path1,
                                                                                                 path2),
                                                                                            edge)));
        }

        fun <P, V, E> of(vertex1: V,
                         path1: P,
                         edges: ImmutableSet<E>,
                         path2: P,
                         vertex2: V): PathBasedGraph<P, V, E> {
            return DefaultTwoToManyEdgePathBasedGraph(verticesByPath = persistentMapOf(Pair(path1,
                                                                                            vertex1),
                                                                                       Pair(path2,
                                                                                            vertex2)),
                                                      edgesSetByPathPair = persistentMapOf(Pair(Pair(path1,
                                                                                                     path2),
                                                                                                edges.toPersistentSet())));
        }

    }

    val verticesByPath: PersistentMap<P, V>

    fun verticesAsStream(): Stream<V> {
        return verticesByPath.values.stream()
    }

    fun edgesAsStream(): Stream<E>

    fun connectedPaths(): Stream<Pair<P, P>>

    fun <R> fold(twoToOne: (PersistentMap<P, V>, PersistentMap<Pair<P, P>, E>) -> R,
                 twoToMany: (PersistentMap<P, V>, PersistentMap<Pair<P, P>, PersistentSet<E>>) -> R): R

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
                                        getVertex(p1).zip(getEdgesFromPathToPath(p1,
                                                                                 p2).some(),
                                                          getVertex(p2)) { v1: V, es: ImmutableSet<E>, v2: V ->
                                            es.fold(persistentSetOf<Tuple5<V, P, E, P, V>>(),
                                                    { set, edge ->
                                                        set.add(Tuple5(v1,
                                                                       p1,
                                                                       edge,
                                                                       p2,
                                                                       v2))
                                                    })
                                        }
                                                .getOrElse { persistentSetOf() }
                                    })
                .flatMap { set: PersistentSet<Tuple5<V, P, E, P, V>> -> set.stream() }
                .spliterator()

    }

    fun getVertex(path: P): Option<V>

    fun putVertex(path: P,
                  vertex: V): PathBasedGraph<P, V, E>

    fun putVertex(vertex: V,
                  pathExtractor: (V) -> P): PathBasedGraph<P, V, E> {
        return putVertex(pathExtractor.invoke(vertex),
                         vertex)
    }

    fun <M : Map<out P, @UnsafeVariance V>> putAllVertices(vertices: M): PathBasedGraph<P, V, E>

    fun putEdge(path1: P,
                path2: P,
                edge: E): PathBasedGraph<P, V, E>

    fun putEdge(connectedPaths: Pair<P, P>,
                edge: E): PathBasedGraph<P, V, E>

    fun putEdge(edge: E,
                pathsExtractor: (E) -> Pair<P, P>): PathBasedGraph<P, V, E> {
        return pathsExtractor.invoke(edge)
                .let { pair ->
                    putEdge(pair.first,
                            pair.second,
                            edge)
                }
    }

    fun <M : Map<Pair<P, P>, @UnsafeVariance E>> putAllEdges(edges: M): PathBasedGraph<P, V, E>

    fun <S : Set<@UnsafeVariance E>, M : Map<Pair<P, P>, S>> putAllEdgeSets(edges: M): PathBasedGraph<P, V, E>

    fun getEdgesFromPathToPath(path1: P,
                               path2: P): ImmutableSet<E>

    fun getEdgesFrom(path: P): Stream<E> {
        return connectedPaths().filter { pair: Pair<P, P> -> pair.first == path }
                .map { pair: Pair<P, P> ->
                    getEdgesFromPathToPath(path1 = pair.first,
                                           path2 = pair.second)
                }
                .flatMap { edges: ImmutableSet<E> -> edges.stream() }
    }

    fun getEdgesTo(path: P): Stream<E> {
        return connectedPaths().filter { pair: Pair<P, P> -> pair.second == path }
                .map { pair: Pair<P, P> ->
                    getEdgesFromPathToPath(path1 = pair.first,
                                           path2 = pair.second)
                }
                .flatMap { edges: ImmutableSet<E> -> edges.stream() }
    }

    fun getFullConnectionFromPathToPath(path1: P,
                                        path2: P): ImmutableSet<Tuple5<V, P, E, P, V>> {
        return getVertex(path1).zip(getEdgesFromPathToPath(path1,
                                                           path2).some(),
                                    getVertex(path2)) { v1: V, es: ImmutableSet<E>, v2: V ->
            es.fold(persistentSetOf<Tuple5<V, P, E, P, V>>()) { set, edge ->
                set.add(Tuple5(v1,
                               path1,
                               edge,
                               path2,
                               v2))
            }
        }
                .getOrElse { persistentSetOf() }
    }

    fun filterVertices(function: (V) -> Boolean): PathBasedGraph<P, V, E>

    fun filterEdges(function: (E) -> Boolean): PathBasedGraph<P, V, E>

    fun <R> mapVertices(function: (V) -> R): PathBasedGraph<P, R, E>

    fun <R> mapEdges(function: (E) -> R): PathBasedGraph<P, V, R>

    fun <R> flatMapVertices(function: (V) -> PathBasedGraph<P, R, E>): PathBasedGraph<P, R, E>

    fun <R> flatMapEdges(function: (E) -> PathBasedGraph<P, V, R>): PathBasedGraph<P, V, R>

    fun hasCycles(): Boolean

    fun getCycles(): Stream<Pair<Triple<P, P, E>, Triple<P, P, E>>>

    fun createMinimumSpanningTreeGraphUsingEdgeCostFunction(costComparator: Comparator<E>): PathBasedGraph<P, V, E>

    fun depthFirstSearchOnPath(path: P): Stream<Tuple5<V, P, E, P, V>>

    fun successors(vertexPath: P): Stream<Pair<P, V>>

    fun successors(vertex: V,
                   pathExtractor: (V) -> P): Stream<Pair<P, V>>

    fun predecessors(vertexPath: P): Stream<Pair<P, V>>

    fun predecessors(vertex: V,
                     pathExtractor: (V) -> P): Stream<Pair<P, V>>

    fun adjacentVertices(vertexPath: P): Stream<Pair<P, V>>

    fun adjacentVertices(vertex: V,
                         pathExtractor: (V) -> P): Stream<Pair<P, V>>


}