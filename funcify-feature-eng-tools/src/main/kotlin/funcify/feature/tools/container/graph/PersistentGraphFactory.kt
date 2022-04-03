package funcify.feature.tools.container.graph

import arrow.core.Option
import arrow.core.Tuple5
import arrow.core.getOrElse
import arrow.core.some
import arrow.core.toOption
import funcify.feature.tools.container.tree.UnionFindTree
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import java.util.stream.Collectors
import java.util.stream.Stream
import java.util.stream.StreamSupport


/**
 *
 * @author smccarron
 * @created 2/7/22
 */
internal object PersistentGraphFactory {

    private fun <T1, T2> Pair<T1, T2>.swap(): Pair<T2, T1> {
        return Pair(this.second,
                    this.first)
    }

    data class PathBasedGraph<P, V, E>(val vertices: PersistentMap<P, V>,
                                       val edges: PersistentMap<Pair<P, P>, E>) : PersistentGraph<P, V, E> {

        /**
         * Lazily initialized path_connections map: e.g.
         * { ( parent_path_0 to child_path_0 ), ... ( parent_path_n-1 to child_path_n-1 ) }
         * detailing what "child" paths each path has if directionality is being
         * used in the algorithm in question
         */
        private val pathConnections: ImmutableMap<P, ImmutableSet<P>> by lazy {
            edges.keys.stream()
                    .parallel()
                    .collect(Collectors.groupingBy { pair: Pair<P, P> ->
                        pair.first
                    }).entries.stream()
                    .parallel()
                    .map { entry: MutableMap.MutableEntry<P, MutableList<Pair<P, P>>> ->
                        Pair(entry.key,
                             entry.value.stream()
                                     .map { pair: Pair<P, P> -> pair.second }
                                     .reduce(persistentSetOf<P>(),
                                             { ps, p -> ps.add(p) },
                                             { ps1, ps2 -> ps1.addAll(ps2) }))
                    }
                    .reduce(persistentMapOf<P, PersistentSet<P>>(),
                            { pm, p ->
                                pm.put(p.first,
                                       p.second)
                            },
                            { pm1, pm2 -> pm1.putAll(pm2) })
        }

        override fun verticesByPath(): PersistentMap<P, V> {
            return vertices
        }

        override fun edgesByPathPair(): PersistentMap<Pair<P, P>, E> {
            return edges
        }

        override fun getVertex(path: P): Option<V> {
            return vertices[path].toOption()
        }

        override fun putVertex(path: P,
                               vertex: V): PersistentGraph<P, V, E> {
            return PathBasedGraph(vertices.put(path,
                                               vertex),
                                  edges)
        }

        /**
         * Do not add an edge that does not have a corresponding vertex path
         */
        override fun putEdge(path1: P,
                             path2: P,
                             edge: E): PersistentGraph<P, V, E> {
            return if (vertices.containsKey(path1) && vertices.containsKey(path2)) {
                PathBasedGraph(vertices,
                               edges.put(Pair(path1,
                                              path2),
                                         edge))
            } else {
                this
            }
        }

        override fun putEdge(connectedPaths: Pair<P, P>,
                             edge: E): PersistentGraph<P, V, E> {
            return if (vertices.containsKey(connectedPaths.first) && vertices.containsKey(connectedPaths.second)) {
                PathBasedGraph(vertices,
                               edges.put(connectedPaths,
                                         edge))
            } else {
                this
            }
        }

        override fun getEdgeFromPathToPath(path1: P,
                                           path2: P): Option<E> {
            return edges.get(Pair(path1,
                                  path2))
                    .toOption()
        }

        /**
         * Overrides default implementation making use of lazily
         * initiated path_connections map, which should be faster with
         * larger graphs since not all path connections have to be
         * streamed through
         */
        override fun getEdgesFrom(path: P): Stream<E> {
            return if (pathConnections.containsKey(path)) {
                pathConnections[path].toOption()
                        .map { ps: ImmutableSet<P> ->
                            ps.stream()
                                    .map { p: P -> path to p }
                        }
                        .getOrElse { Stream.empty() }
                        .map { pair: Pair<P, P> -> edges[pair].toOption() }
                        .flatMap { eOpt: Option<E> ->
                            eOpt.fold({ Stream.empty() },
                                      { e -> Stream.of(e) })
                        }
            } else {
                Stream.empty<E>()
            }
        }

        /**
         * Overrides default implementation making use of lazily
         * initiated path_connections map, which should be faster with
         * larger graphs since not all path connections have to be
         * streamed through
         */
        override fun getEdgesTo(path: P): Stream<E> {
            return if (pathConnections.containsKey(path)) {
                pathConnections[path].toOption()
                        .map { ps: ImmutableSet<P> ->
                            ps.stream()
                                    .map { p: P -> p to path }
                        }
                        .getOrElse { Stream.empty() }
                        .map { pair: Pair<P, P> -> edges[pair].toOption() }
                        .flatMap { eOpt: Option<E> ->
                            eOpt.fold({ Stream.empty() },
                                      { e -> Stream.of(e) })
                        }
            } else {
                Stream.empty<E>()
            }
        }

        override fun filterVertices(function: (V) -> Boolean): PersistentGraph<P, V, E> {
            val updatedVertices = vertices.asSequence()
                    .filter { entry: Map.Entry<P, V> -> function.invoke(entry.value) }
                    .fold(persistentMapOf<P, V>()) { acc: PersistentMap<P, V>, entry: Map.Entry<P, V> ->
                        acc.put(entry.key,
                                entry.value)
                    }
            val updatedEdges = edges.asSequence()
                    .filter { entry: Map.Entry<Pair<P, P>, E> ->
                        sequenceOf(entry.key.first,
                                   entry.key.second).all { p ->
                            updatedVertices.containsKey(p)
                        }
                    }
                    .fold(persistentMapOf<Pair<P, P>, E>()) { acc: PersistentMap<Pair<P, P>, E>, entry: Map.Entry<Pair<P, P>, E> ->
                        acc.put(entry.key,
                                entry.value)
                    }
            return PathBasedGraph(updatedVertices,
                                  updatedEdges)
        }

        override fun filterEdges(function: (E) -> Boolean): PersistentGraph<P, V, E> {
            val updatedEdges = edges.asSequence()
                    .filter { entry: Map.Entry<Pair<P, P>, E> ->
                        function.invoke(entry.value)
                    }
                    .fold(persistentMapOf<Pair<P, P>, E>()) { acc: PersistentMap<Pair<P, P>, E>, entry: Map.Entry<Pair<P, P>, E> ->
                        acc.put(entry.key,
                                entry.value)
                    }
            return PathBasedGraph(vertices,
                                  updatedEdges)
        }

        override fun <R> mapVertices(function: (V) -> R): PersistentGraph<P, R, E> {
            val updatedVertices = vertices.asSequence()
                    .map { entry: Map.Entry<P, V> -> entry.key to function.invoke(entry.value) }
                    .fold(persistentMapOf<P, R>()) { acc: PersistentMap<P, R>, entry: Pair<P, R> ->
                        acc.put(entry.first,
                                entry.second)
                    }
            val updatedEdges = edges.asSequence()
                    .filter { entry: Map.Entry<Pair<P, P>, E> ->
                        sequenceOf(entry.key.first,
                                   entry.key.second).all { p ->
                            updatedVertices.containsKey(p)
                        }
                    }
                    .fold(persistentMapOf<Pair<P, P>, E>()) { acc: PersistentMap<Pair<P, P>, E>, entry: Map.Entry<Pair<P, P>, E> ->
                        acc.put(entry.key,
                                entry.value)
                    }
            return PathBasedGraph(updatedVertices,
                                  updatedEdges)
        }

        override fun <R> mapEdges(function: (E) -> R): PersistentGraph<P, V, R> {
            val updatedEdges = edges.asSequence()
                    .map { entry: Map.Entry<Pair<P, P>, E> ->
                        entry.key to function.invoke(entry.value)
                    }
                    .fold(persistentMapOf<Pair<P, P>, R>()) { acc: PersistentMap<Pair<P, P>, R>, entry: Pair<Pair<P, P>, R> ->
                        acc.put(entry.first,
                                entry.second)
                    }
            return PathBasedGraph(vertices,
                                  updatedEdges)
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
                    .fold(Pair(persistentMapOf<P, R>(),
                               edges.asSequence())) { accPair, pair ->
                        Pair(pair.first.asSequence()
                                     .fold(accPair.first) { vertMap: PersistentMap<P, R>, entry: Map.Entry<P, R> ->
                                         vertMap.put(entry.key,
                                                     entry.value)
                                     },
                             accPair.second.plus(pair.second))
                    }
                    .let { pair: Pair<PersistentMap<P, R>, Sequence<Map.Entry<Pair<P, P>, E>>> ->
                        PathBasedGraph(pair.first,
                                       pair.second.filter { entry: Map.Entry<Pair<P, P>, E> ->
                                           sequenceOf(entry.key.first,
                                                      entry.key.second).all { p -> pair.first.containsKey(p) }
                                       }
                                               .fold(persistentMapOf<Pair<P, P>, E>()) { acc: PersistentMap<Pair<P, P>, E>, entry: Map.Entry<Pair<P, P>, E> ->
                                                   acc.put(entry.key,
                                                           entry.value)
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
                    .fold(Pair(vertices,
                               sequenceOf<Map.Entry<Pair<P, P>, R>>())) { accPair, pair ->
                        Pair(pair.first.asSequence()
                                     .fold(accPair.first) { vertMap: PersistentMap<P, V>, entry: Map.Entry<P, V> ->
                                         vertMap.put(entry.key,
                                                     entry.value)
                                     },
                             accPair.second.plus(pair.second))
                    }
                    .let { pair: Pair<PersistentMap<P, V>, Sequence<Map.Entry<Pair<P, P>, R>>> ->
                        PathBasedGraph(pair.first,
                                       pair.second.filter { entry: Map.Entry<Pair<P, P>, R> ->
                                           sequenceOf(entry.key.first,
                                                      entry.key.second).all { p -> pair.first.containsKey(p) }
                                       }
                                               .fold(persistentMapOf<Pair<P, P>, R>()) { acc: PersistentMap<Pair<P, P>, R>, entry: Map.Entry<Pair<P, P>, R> ->
                                                   acc.put(entry.key,
                                                           entry.value)
                                               })
                    }

        }

        override fun hasCycles(): Boolean {
            return edges.asSequence()
                    .map { entry: Map.Entry<Pair<P, P>, E> -> entry.key.swap() }
                    .any { pair: Pair<P, P> -> edges.containsKey(pair) }
        }

        override fun getCycles(): Sequence<Pair<Triple<P, P, E>, Triple<P, P, E>>> {
            return edges.asSequence()
                    .filter { entry: Map.Entry<Pair<P, P>, E> -> edges.containsKey(entry.key.swap()) }
                    .map { entry: Map.Entry<Pair<P, P>, E> ->
                        Pair(Triple(entry.key.first,
                                    entry.key.second,
                                    entry.value),
                             Triple(entry.key.second,
                                    entry.key.first,
                                    edges[Pair(entry.key.second,
                                               entry.key.first)]!!))
                    }
        }

        override fun createMinimumSpanningTreeGraphUsingEdgeCostFunction(costComparator: Comparator<E>): PersistentGraph<P, V, E> {
            return edgesByPathPair().asSequence()
                    .sortedWith { e1, e2 ->
                        costComparator.compare(e1.value,
                                               e2.value)
                    }
                    .fold(UnionFindTree.empty<P>() to persistentMapOf()) { acc: Pair<UnionFindTree<P>, PersistentMap<Pair<P, P>, E>>, entry: Map.Entry<Pair<P, P>, E> ->
                        val root1AndUnionFindTree: Pair<Option<P>, UnionFindTree<P>> = acc.first.add(entry.key.first)
                                .add(entry.key.second)
                                .find(entry.key.first)
                        val root2AndUnionFindTree: Pair<Option<P>, UnionFindTree<P>> = root1AndUnionFindTree.second.find(entry.key.second)
                        val rootPath1: Option<P> = root1AndUnionFindTree.first
                        val rootPath2: Option<P> = root2AndUnionFindTree.first
                        if (rootPath1.filter({ r1 ->
                                                 rootPath2.filter { r2 -> r1 != r2 }
                                                         .isDefined()
                                             })
                                        .isDefined()) {
                            root2AndUnionFindTree.second.union(rootPath1.orNull()!!,
                                                               rootPath2.orNull()!!) to acc.second.put(entry.key,
                                                                                                       entry.value)
                        } else {
                            root2AndUnionFindTree.second to acc.second
                        }
                    }
                    .let { pair: Pair<UnionFindTree<P>, PersistentMap<Pair<P, P>, E>> ->
                        PathBasedGraph(vertices = verticesByPath(),
                                       edges = pair.second)
                    }

        }

        override fun depthFirstSearchOnPath(path: P): Stream<Tuple5<V, P, E, P, V>> {
            return path.some()
                    .filter { p: P -> vertices.containsKey(p) }
                    .map { p: P -> Stream.of(p) }
                    .getOrElse { Stream.empty() }
                    .flatMap { p: P ->
                        StreamSupport.stream(DepthFirstSearchSpliterator<P, V, E>(inputPath = p,
                                                                                  vertices = vertices,
                                                                                  edges = edges,
                                                                                  pathConnections = pathConnections),
                                             false)
                    }
        }
    }

}