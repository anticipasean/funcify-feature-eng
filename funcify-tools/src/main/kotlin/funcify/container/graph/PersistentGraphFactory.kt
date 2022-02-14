package funcify.container.graph

import arrow.core.Option
import arrow.core.toOption
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf


/**
 *
 * @author smccarron
 * @created 2/7/22
 */
internal object PersistentGraphFactory {

    data class PathBasedGraph<P, V, E>(val vertices: PersistentMap<P, V>, val edges: PersistentMap<Pair<P, P>, E>) : PersistentGraph<P, V, E> {

        override fun verticesByPath(): PersistentMap<P, V> {
            return vertices
        }

        override fun edgesByPathPair(): PersistentMap<Pair<P, P>, E> {
            return edges
        }

        override fun getVertex(path: P): Option<V> {
            return vertices.get(path)
                    .toOption()
        }

        override fun putVertex(path: P, vertex: V): PersistentGraph<P, V, E> {
            return PathBasedGraph(vertices.put(path, vertex), edges)
        }

        /**
         * Do not add an edge that does not have a corresponding vertex path
         */
        override fun putEdge(path1: P, path2: P, edge: E): PersistentGraph<P, V, E> {
            return if (vertices.containsKey(path1) && vertices.containsKey(path2)) {
                PathBasedGraph(vertices, edges.put(Pair(path1, path2), edge))
            } else {
                this
            }
        }

        override fun getEdgeFromPathToPath(path1: P, path2: P): Option<E> {
            return edges.get(Pair(path1, path2))
                    .toOption()
        }

        override fun filterVertices(function: (V) -> Boolean): PersistentGraph<P, V, E> {
            val updatedVertices = vertices.asSequence()
                    .filter { entry: Map.Entry<P, V> -> function.invoke(entry.value) }
                    .fold(persistentMapOf<P, V>()) { acc: PersistentMap<P, V>, entry: Map.Entry<P, V> ->
                        acc.put(entry.key, entry.value)
                    }
            val updatedEdges = edges.asSequence()
                    .filter { entry: Map.Entry<Pair<P, P>, E> ->
                        sequenceOf(entry.key.first, entry.key.second).all { p ->
                            updatedVertices.containsKey(p)
                        }
                    }
                    .fold(persistentMapOf<Pair<P, P>, E>()) { acc: PersistentMap<Pair<P, P>, E>, entry: Map.Entry<Pair<P, P>, E> ->
                        acc.put(entry.key, entry.value)
                    }
            return PathBasedGraph(updatedVertices, updatedEdges)
        }

        override fun filterEdges(function: (E) -> Boolean): PersistentGraph<P, V, E> {
            val updatedEdges = edges.asSequence()
                    .filter { entry: Map.Entry<Pair<P, P>, E> ->
                        function.invoke(entry.value)
                    }
                    .fold(persistentMapOf<Pair<P, P>, E>()) { acc: PersistentMap<Pair<P, P>, E>, entry: Map.Entry<Pair<P, P>, E> ->
                        acc.put(entry.key, entry.value)
                    }
            return PathBasedGraph(vertices, updatedEdges)
        }

        override fun <R> mapVertices(function: (V) -> R): PersistentGraph<P, R, E> {
            val updatedVertices = vertices.asSequence()
                    .map { entry: Map.Entry<P, V> -> entry.key to function.invoke(entry.value) }
                    .fold(persistentMapOf<P, R>()) { acc: PersistentMap<P, R>, entry: Pair<P, R> ->
                        acc.put(entry.first, entry.second)
                    }
            val updatedEdges = edges.asSequence()
                    .filter { entry: Map.Entry<Pair<P, P>, E> ->
                        sequenceOf(entry.key.first, entry.key.second).all { p ->
                            updatedVertices.containsKey(p)
                        }
                    }
                    .fold(persistentMapOf<Pair<P, P>, E>()) { acc: PersistentMap<Pair<P, P>, E>, entry: Map.Entry<Pair<P, P>, E> ->
                        acc.put(entry.key, entry.value)
                    }
            return PathBasedGraph(updatedVertices, updatedEdges)
        }

        override fun <R> mapEdges(function: (E) -> R): PersistentGraph<P, V, R> {
            val updatedEdges = edges.asSequence()
                    .map { entry: Map.Entry<Pair<P, P>, E> ->
                        entry.key to function.invoke(entry.value)
                    }
                    .fold(persistentMapOf<Pair<P, P>, R>()) { acc: PersistentMap<Pair<P, P>, R>, entry: Pair<Pair<P, P>, R> ->
                        acc.put(entry.first, entry.second)
                    }
            return PathBasedGraph(vertices, updatedEdges)
        }

        override fun <R> flatMapVertices(function: (V) -> PersistentGraph<P, R, E>): PersistentGraph<P, R, E> {
            return vertices.asSequence()
                    .map { entry: Map.Entry<P, V> -> entry.key to function.invoke(entry.value) }
                    .map { pair: Pair<P, PersistentGraph<P, R, E>> -> pair.second }
                    .map { pg: PersistentGraph<P, R, E> ->
                        pg.verticesByPath()
                                .asSequence() to pg.edgesByPathPair()
                                .asSequence()
                    }
                    .fold(Pair(persistentMapOf<P, R>(), edges.asSequence())) { accPair, pair ->
                        Pair(pair.first.asSequence()
                                     .fold(accPair.first) { vertMap: PersistentMap<P, R>, entry: Map.Entry<P, R> ->
                                         vertMap.put(entry.key, entry.value)
                                     }, accPair.second.plus(pair.second))
                    }
                    .let { pair: Pair<PersistentMap<P, R>, Sequence<Map.Entry<Pair<P, P>, E>>> ->
                        PathBasedGraph(pair.first, pair.second.filter { entry: Map.Entry<Pair<P, P>, E> ->
                            sequenceOf(entry.key.first, entry.key.second).all { p -> pair.first.containsKey(p) }
                        }
                                .fold(persistentMapOf<Pair<P, P>, E>()) { acc: PersistentMap<Pair<P, P>, E>, entry: Map.Entry<Pair<P, P>, E> ->
                                    acc.put(entry.key, entry.value)
                                })
                    }

        }

        override fun <R> flatMapEdges(function: (E) -> PersistentGraph<P, V, R>): PersistentGraph<P, V, R> {
            return edges.asSequence()
                    .map { entry: Map.Entry<Pair<P, P>, E> -> entry.key to function.invoke(entry.value) }
                    .map { pair: Pair<Pair<P, P>, PersistentGraph<P, V, R>> -> pair.second }
                    .map { pg: PersistentGraph<P, V, R> ->
                        pg.verticesByPath()
                                .asSequence() to pg.edgesByPathPair()
                                .asSequence()
                    }
                    .fold(Pair(vertices, sequenceOf<Map.Entry<Pair<P, P>, R>>())) { accPair, pair ->
                        Pair(pair.first.asSequence()
                                     .fold(accPair.first) { vertMap: PersistentMap<P, V>, entry: Map.Entry<P, V> ->
                                         vertMap.put(entry.key, entry.value)
                                     }, accPair.second.plus(pair.second))
                    }
                    .let { pair: Pair<PersistentMap<P, V>, Sequence<Map.Entry<Pair<P, P>, R>>> ->
                        PathBasedGraph(pair.first, pair.second.filter { entry: Map.Entry<Pair<P, P>, R> ->
                            sequenceOf(entry.key.first, entry.key.second).all { p -> pair.first.containsKey(p) }
                        }
                                .fold(persistentMapOf<Pair<P, P>, R>()) { acc: PersistentMap<Pair<P, P>, R>, entry: Map.Entry<Pair<P, P>, R> ->
                                    acc.put(entry.key, entry.value)
                                })
                    }

        }


    }

}