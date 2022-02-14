package funcify.container.graph

import arrow.core.Option
import arrow.core.Tuple5
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf


/**
 *
 * @author smccarron
 * @created 2/7/22
 */
interface PersistentGraph<P, V, E> : Iterable<Tuple5<P, V, E, P, V>> {

    companion object {

        fun <P, V, E> empty(): PersistentGraph<P, V, E> {
            return PersistentGraphFactory.PathBasedGraph(persistentMapOf(), persistentMapOf())
        }

        fun <P, V, E> of(path: P, vertex: V): PersistentGraph<P, V, E> {
            return PersistentGraphFactory.PathBasedGraph(persistentMapOf(Pair(path, vertex)), persistentMapOf());
        }

        fun <P, V, E> of(path1: P, vertex1: V, edge: E, path2: P, vertex2: V): PersistentGraph<P, V, E> {
            return PersistentGraphFactory.PathBasedGraph(persistentMapOf(Pair(path1, vertex1), Pair(path2, vertex2)),
                                                         persistentMapOf(Pair(Pair(path1, path2), edge)));
        }

    }

    fun verticesByPath(): PersistentMap<P, V>

    fun edgesByPathPair(): PersistentMap<Pair<P, P>, E>

    fun vertices(): Sequence<V> {
        return verticesByPath().values.asSequence()
    }

    fun edges(): Sequence<E> {
        return edgesByPathPair().values.asSequence()
    }

    fun connectedPaths(): Sequence<Pair<P, P>> {
        return edgesByPathPair().keys.asSequence()
    }

    /**
     * Each tuple containing one full connection: vertex 1's path -> vertex 1 -> the edge -> vertex 2's path -> vertex 2
     * iterator will not include disconnected vertices, those without an edge to another vertex
     */
    override fun iterator(): Iterator<Tuple5<P, V, E, P, V>> {
        return connectedPaths().map { (p1: P, p2: P) ->
            getVertex(p1).zip(getEdgeFromPathToPath(p1, p2), getVertex(p2)) { v1: V, e: E, v2: V ->
                Tuple5(p1, v1, e, p2, v2)
            }
        }
                .flatMap { option: Option<Tuple5<P, V, E, P, V>> ->
                    option.fold({ emptySequence() }) { t: Tuple5<P, V, E, P, V> -> sequenceOf(t) }
                }
                .iterator()
    }

    fun getVertex(path: P): Option<V>

    fun putVertex(path: P, vertex: V): PersistentGraph<P, V, E>

    fun putVertex(vertex: V, pathExtractor: (V) -> P): PersistentGraph<P, V, E> {
        return putVertex(pathExtractor.invoke(vertex), vertex)
    }

    fun putEdge(path1: P, path2: P, edge: E): PersistentGraph<P, V, E>

    fun putEdge(edge: E, pathsExtractor: (E) -> Pair<P, P>): PersistentGraph<P, V, E> {
        return pathsExtractor.invoke(edge)
                .let { pair -> putEdge(pair.first, pair.second, edge) }
    }

    fun getEdgeFromPathToPath(path1: P, path2: P): Option<E>

    fun getEdgesFrom(path: P): Sequence<E> {
        return connectedPaths().filter { pair: Pair<P, P> -> pair.first == path }
                .map { pair: Pair<P, P> -> getEdgeFromPathToPath(path1 = pair.first, path2 = pair.second) }
                .flatMap { edgeOption: Option<E> -> edgeOption.fold({ emptySequence() }) { e: E -> sequenceOf(e) } }
    }

    fun getEdgesTo(path: P): Sequence<E> {
        return connectedPaths().filter { pair: Pair<P, P> -> pair.second == path }
                .map { pair: Pair<P, P> -> getEdgeFromPathToPath(path1 = pair.first, path2 = pair.second) }
                .flatMap { edgeOption: Option<E> -> edgeOption.fold({ emptySequence() }) { e: E -> sequenceOf(e) } }
    }

    fun getFullConnectionFromPathToPath(path1: P, path2: P): Option<Tuple5<V, P, E, P, V>> {
        return getVertex(path1).zip(getEdgeFromPathToPath(path1, path2), getVertex(path2)) { v1: V, e: E, v2: V ->
            Tuple5(v1, path1, e, path2, v2)
        }
    }

    fun filterVertices(function: (V) -> Boolean): PersistentGraph<P, V, E>

    fun filterEdges(function: (E) -> Boolean): PersistentGraph<P, V, E>

    fun <R> mapVertices(function: (V) -> R): PersistentGraph<P, R, E>

    fun <R> mapEdges(function: (E) -> R): PersistentGraph<P, V, R>

    fun <R> flatMapVertices(function: (V) -> PersistentGraph<P, R, E>): PersistentGraph<P, R, E>

    fun <R> flatMapEdges(function: (E) -> PersistentGraph<P, V, R>): PersistentGraph<P, V, R>
}