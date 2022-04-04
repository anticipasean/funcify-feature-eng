package funcify.feature.tools.container.graph

import arrow.core.Option
import arrow.core.Tuple5
import arrow.core.getOrElse
import arrow.core.some
import arrow.core.toOption
import funcify.feature.tools.container.tree.UnionFindTree
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import java.util.stream.Stream
import java.util.stream.StreamSupport

internal data class DefaultTwoToOnePathToEdgePathBasedGraph<P, V, E>(override val verticesByPath: PersistentMap<P, V> = persistentMapOf(),
                                                                     override val edgesByPathPair: PersistentMap<Pair<P, P>, E> = persistentMapOf()) : TwoToOnePathToEdgeGraph<P, V, E> {

    companion object {

        private fun <T1, T2> Pair<T1, T2>.swap(): Pair<T2, T1> {
            return Pair(this.second,
                        this.first)
        }

        private fun <K, V> ImmutableMap<K, V>.stream(): Stream<Map.Entry<K, V>> {
            return this.entries.stream()
        }

        private fun <T> Option<T>.stream(): Stream<T> {
            return this.fold({ Stream.empty() },
                             { t: T -> Stream.of(t) })
        }

        private fun <T> Option<T>.toPersistentSet(): PersistentSet<T> {
            return this.fold({ persistentSetOf() },
                             { t: T -> persistentSetOf(t) })
        }

    }

    override val vertices: PersistentList<V> by lazy {
        verticesByPath.stream()
                .map { e -> e.value }
                .reduce(persistentListOf<V>(),
                        { pl, v -> pl.add(v) },
                        { pl1, pl2 -> pl1.addAll(pl2) })
    }

    override val edgesByConnectedVertices: PersistentMap<Pair<V, V>, PersistentSet<E>> by lazy {
        edgesByPathPair.stream()
                .map { e: Map.Entry<Pair<P, P>, E> ->
                    verticesByPath[e.key.first].toOption()
                            .zip(verticesByPath[e.key.second].toOption()) { v1: V, v2: V ->
                                Triple(v1,
                                       v2,
                                       e.value)
                            }
                }
                .flatMap { tOpt: Option<Triple<V, V, E>> -> tOpt.stream() }
                .reduce(persistentMapOf<Pair<V, V>, PersistentSet<E>>(),
                        { pl, triple ->
                            val vertexPair: Pair<V, V> = triple.first to triple.second
                            pl.put(vertexPair,
                                   pl.getOrDefault(vertexPair,
                                                   persistentSetOf())
                                           .add(triple.third))
                        },
                        { pl1, pl2 -> pl1.putAll(pl2) })

    }

    /**
     * Lazily initialized path_connections map: e.g.
     * { ( parent_path_0 to child_path_0 ), ... ( parent_path_n-1 to child_path_n-1 ) }
     * detailing what "child" paths each path has if directionality is being
     * used in the algorithm in question
     */
    private val pathConnections: ImmutableMap<P, ImmutableSet<P>> by lazy {
        edgesByPathPair.keys.stream()
                .reduce(persistentMapOf<P, PersistentSet<P>>(),
                        { pm, p ->
                            pm.put(p.first,
                                   pm.getOrDefault(p.first,
                                                   persistentSetOf())
                                           .add(p.second))
                        },
                        { pm1, pm2 -> pm1.putAll(pm2) })
    }


    override fun edgesAsStream(): Stream<E> {
        return edgesByPathPair.values.stream()
    }

    override fun connectedPaths(): Stream<Pair<P, P>> {
        return edgesByPathPair.keys.stream()
    }

    override fun getVertex(path: P): Option<V> {
        return verticesByPath[path].toOption()
    }

    override fun putVertex(path: P,
                           vertex: V): PathBasedGraph<P, V, E> {
        return DefaultTwoToOnePathToEdgePathBasedGraph(verticesByPath.put(path,
                                                                          vertex),
                                                       edgesByPathPair)
    }

    override fun <M : Map<out P, @UnsafeVariance V>> putAllVertices(vertices: M): PathBasedGraph<P, V, E> {
        return DefaultTwoToOnePathToEdgePathBasedGraph<P, V, E>(verticesByPath = verticesByPath.putAll(vertices),
                                                                edgesByPathPair = edgesByPathPair)
    }

    /**
     * Do not add an edge that does not have a corresponding vertex path
     */
    override fun putEdge(path1: P,
                         path2: P,
                         edge: E): PathBasedGraph<P, V, E> {
        return if (verticesByPath.containsKey(path1) && verticesByPath.containsKey(path2)) {
            DefaultTwoToOnePathToEdgePathBasedGraph(verticesByPath = verticesByPath,
                                                    edgesByPathPair = edgesByPathPair.put(Pair(path1,
                                                                                               path2),
                                                                                          edge))
        } else {
            this
        }
    }

    override fun putEdge(connectedPaths: Pair<P, P>,
                         edge: E): PathBasedGraph<P, V, E> {
        return if (verticesByPath.containsKey(connectedPaths.first) && verticesByPath.containsKey(connectedPaths.second)) {
            DefaultTwoToOnePathToEdgePathBasedGraph(verticesByPath = verticesByPath,
                                                    edgesByPathPair = edgesByPathPair.put(connectedPaths,
                                                                                          edge))
        } else {
            this
        }
    }

    override fun <M : Map<out Pair<P, P>, @UnsafeVariance E>> putAllEdges(edges: M): PathBasedGraph<P, V, E> {
        val updatedEdges = edges.entries.parallelStream()
                .reduce(edgesByPathPair,
                        { eMap, entry ->
                            if (verticesByPath.containsKey(entry.key.first) && verticesByPath.containsKey(entry.key.second)) {
                                eMap.put(entry.key,
                                         entry.value)
                            } else {
                                eMap
                            }
                        },
                        { eMap1, eMap2 ->
                            eMap1.putAll(eMap2)
                        })
        return DefaultTwoToOnePathToEdgePathBasedGraph(verticesByPath = verticesByPath,
                                                       edgesByPathPair = updatedEdges)
    }

    override fun <S : Set<@UnsafeVariance E>, M : Map<out Pair<P, P>, S>> putAllEdgeSets(edges: M): PathBasedGraph<P, V, E> {
        val updatedEdges = edges.entries.parallelStream()
                .flatMap { e ->
                    e.value.parallelStream()
                            .map { edge -> e.key to edge }
                }
                .reduce(edgesByPathPair,
                        { eMap, entry ->
                            if (verticesByPath.containsKey(entry.first.first) && verticesByPath.containsKey(entry.first.second)) {
                                eMap.put(entry.first,
                                         entry.second)
                            } else {
                                eMap
                            }
                        },
                        { eMap1, eMap2 ->
                            eMap1.putAll(eMap2)
                        })
        return DefaultTwoToOnePathToEdgePathBasedGraph(verticesByPath = verticesByPath,
                                                       edgesByPathPair = updatedEdges)
    }

    override fun getEdgesFromPathToPath(path1: P,
                                        path2: P): ImmutableSet<E> {
        return edgesByPathPair[Pair(path1,
                                    path2)].toOption()
                .toPersistentSet()
    }

    /**
     * Overrides default implementation making use of lazily
     * initiated path_connections map, which should be faster with
     * larger graphs since not all path connections have to be
     * streamed through
     */
    override fun getEdgesFrom(path: P): Stream<E> {
        return pathConnections[path].toOption()
                .map { ps: ImmutableSet<P> ->
                    ps.stream()
                            .map { p: P -> path to p }
                }
                .getOrElse { Stream.empty() }
                .map { pair: Pair<P, P> -> edgesByPathPair[pair].toOption() }
                .flatMap { eOpt: Option<E> ->
                    eOpt.stream()
                }
    }

    /**
     * Overrides default implementation making use of lazily
     * initiated path_connections map, which should be faster with
     * larger graphs since not all path connections have to be
     * streamed through
     */
    override fun getEdgesTo(path: P): Stream<E> {
        return pathConnections[path].toOption()
                .map { ps: ImmutableSet<P> ->
                    ps.stream()
                            .map { p: P -> p to path }
                }
                .getOrElse { Stream.empty() }
                .map { pair: Pair<P, P> -> edgesByPathPair[pair].toOption() }
                .flatMap { eOpt: Option<E> ->
                    eOpt.stream()
                }
    }

    override fun successors(vertexPath: P): Stream<Pair<P, V>> {
        return pathConnections[vertexPath].toOption()
                .getOrElse { persistentSetOf() }
                .stream()
                .filter { p: P -> p != vertexPath }
                .flatMap { p: P ->
                    getVertex(p).map({ v ->
                                         p to v
                                     })
                            .stream()
                }
    }

    override fun successors(vertex: V,
                            pathExtractor: Function1<V, P>): Stream<Pair<P, V>> {
        return successors(pathExtractor.invoke(vertex))
    }

    override fun predecessors(vertexPath: P): Stream<Pair<P, V>> {
        return pathConnections.stream()
                .filter { (_, value) ->
                    value.stream()
                            .anyMatch { p: P ->
                                p == vertexPath
                            }
                }
                .map { (key, _) -> key }
                .filter { p: P -> p != vertexPath }
                .flatMap { p: P ->
                    verticesByPath[p].toOption()
                            .map { v: V ->
                                p to v
                            }
                            .stream()
                }
    }

    override fun predecessors(vertex: V,
                              pathExtractor: Function1<V, P>): Stream<Pair<P, V>> {
        return predecessors(pathExtractor.invoke(vertex))
    }

    override fun adjacentVertices(vertexPath: P): Stream<Pair<P, V>> {
        return Stream.concat(predecessors(vertexPath),
                             successors(vertexPath))

    }

    override fun adjacentVertices(vertex: V,
                                  pathExtractor: (V) -> P): Stream<Pair<P, V>> {
        return adjacentVertices(pathExtractor.invoke(vertex))
    }


    override fun filterVertices(function: (V) -> Boolean): PathBasedGraph<P, V, E> {
        val updatedVertices = verticesByPath.stream()
                .parallel()
                .filter { entry: Map.Entry<P, V> -> function.invoke(entry.value) }
                .reduce(persistentMapOf<P, V>(),
                        { acc, entry ->
                            acc.put(entry.key,
                                    entry.value)
                        },
                        { pm1, pm2 -> pm1.putAll(pm2) })
        val updatedEdges = edgesByPathPair.stream()
                .parallel()
                .filter { entry: Map.Entry<Pair<P, P>, E> ->
                    sequenceOf(entry.key.first,
                               entry.key.second).all { p ->
                        updatedVertices.containsKey(p)
                    }
                }
                .reduce(persistentMapOf<Pair<P, P>, E>(),
                        { acc, entry ->
                            acc.put(entry.key,
                                    entry.value)
                        },
                        { pm1, pm2 -> pm1.putAll(pm2) })
        return DefaultTwoToOnePathToEdgePathBasedGraph(updatedVertices,
                                                       updatedEdges)
    }

    override fun filterEdges(function: (E) -> Boolean): PathBasedGraph<P, V, E> {
        val updatedEdges = edgesByPathPair.stream()
                .parallel()
                .filter { entry: Map.Entry<Pair<P, P>, E> ->
                    function.invoke(entry.value)
                }
                .reduce(persistentMapOf<Pair<P, P>, E>(),
                        { acc, entry ->
                            acc.put(entry.key,
                                    entry.value)
                        },
                        { pm1, pm2 -> pm1.putAll(pm2) })
        return DefaultTwoToOnePathToEdgePathBasedGraph(verticesByPath = verticesByPath,
                                                       edgesByPathPair = updatedEdges)
    }

    override fun <R> mapVertices(function: (V) -> R): PathBasedGraph<P, R, E> {
        val updatedVertices = verticesByPath.stream()
                .parallel()
                .map { entry: Map.Entry<P, V> -> entry.key to function.invoke(entry.value) }
                .reduce(persistentMapOf<P, R>(),
                        { acc, entry ->
                            acc.put(entry.first,
                                    entry.second)
                        },
                        { pm1, pm2 -> pm1.putAll(pm2) })
        val updatedEdges = edgesByPathPair.stream()
                .parallel()
                .filter { entry: Map.Entry<Pair<P, P>, E> ->
                    sequenceOf(entry.key.first,
                               entry.key.second).all { p ->
                        updatedVertices.containsKey(p)
                    }
                }
                .reduce(persistentMapOf<Pair<P, P>, E>(),
                        { acc, entry ->
                            acc.put(entry.key,
                                    entry.value)
                        },
                        { pm1, pm2 ->
                            pm1.putAll(pm2)
                        })
        return DefaultTwoToOnePathToEdgePathBasedGraph(verticesByPath = updatedVertices,
                                                       edgesByPathPair = updatedEdges)
    }

    override fun <R> mapEdges(function: (E) -> R): PathBasedGraph<P, V, R> {
        val updatedEdges = edgesByPathPair.stream()
                .parallel()
                .map { entry: Map.Entry<Pair<P, P>, E> ->
                    entry.key to function.invoke(entry.value)
                }
                .reduce(persistentMapOf<Pair<P, P>, R>(),
                        { acc, entry ->
                            acc.put(entry.first,
                                    entry.second)
                        },
                        { pm1, pm2 ->
                            pm1.putAll(pm2)
                        })
        return DefaultTwoToOnePathToEdgePathBasedGraph(verticesByPath = verticesByPath,
                                                       edgesByPathPair = updatedEdges)
    }

    override fun <R> flatMapVertices(function: (V) -> PathBasedGraph<P, R, E>): PathBasedGraph<P, R, E> {
        return verticesByPath.stream()
                .parallel()
                .map { entry: Map.Entry<P, V> -> function.invoke(entry.value) }
                .reduce(DefaultTwoToOnePathToEdgePathBasedGraph<P, R, E>() as PathBasedGraph<P, R, E>,
                        { currentGraph: PathBasedGraph<P, R, E>, pg: PathBasedGraph<P, R, E> ->
                            when (currentGraph) {
                                is TwoToOnePathToEdgeGraph -> {
                                    pg.fold({ v: PersistentMap<P, R>, eSingle: PersistentMap<Pair<P, P>, E> ->
                                                currentGraph.putAllVertices(v)
                                                        .putAllEdges(eSingle)
                                            },
                                            { v: PersistentMap<P, R>, eMany: PersistentMap<Pair<P, P>, PersistentSet<E>> -> // Any output of persistent graph that has
                                                // two to many mapping means the current graph type must be flipped to two-to-many edge map type
                                                // in order to avoid any loss of information
                                                DefaultTwoToManyEdgePathBasedGraph<P, R, E>(verticesByPath = currentGraph.verticesByPath).putAllEdges(currentGraph.edgesByPathPair)
                                                        .putAllVertices(v)
                                                        .putAllEdgeSets(eMany)
                                            })
                                }
                                is TwoToManyPathToEdgeGraph -> {
                                    pg.fold({ v: PersistentMap<P, R>, eSingle: PersistentMap<Pair<P, P>, E> ->
                                                currentGraph.putAllVertices(v)
                                                        .putAllEdges(eSingle)
                                            },
                                            { v: PersistentMap<P, R>, eMany: PersistentMap<Pair<P, P>, PersistentSet<E>> ->
                                                currentGraph.putAllVertices(v)
                                                        .putAllEdgeSets(eMany)
                                            })
                                }
                            }
                        },
                        { pg1, pg2 ->
                            when (pg1) {
                                is TwoToOnePathToEdgeGraph -> {
                                    pg2.fold({ v: PersistentMap<P, R>, eSingle: PersistentMap<Pair<P, P>, E> ->
                                                 pg1.putAllVertices(v)
                                                         .putAllEdges(eSingle)
                                             },
                                             { v: PersistentMap<P, R>, eMany: PersistentMap<Pair<P, P>, PersistentSet<E>> ->
                                                 DefaultTwoToManyEdgePathBasedGraph<P, R, E>(verticesByPath = pg1.verticesByPath).putAllEdges(pg1.edgesByPathPair)
                                                         .putAllVertices(v)
                                                         .putAllEdgeSets(eMany)
                                             })
                                }
                                is TwoToManyPathToEdgeGraph -> {
                                    pg2.fold({ v: PersistentMap<P, R>, eSingle: PersistentMap<Pair<P, P>, E> ->
                                                 pg1.putAllVertices(v)
                                                         .putAllEdges(eSingle)
                                             },
                                             { v: PersistentMap<P, R>, eMany: PersistentMap<Pair<P, P>, PersistentSet<E>> ->
                                                 pg1.putAllVertices(v)
                                                         .putAllEdgeSets(eMany)
                                             })
                                }
                            }
                        })

    }

    override fun <R> flatMapEdges(function: (E) -> PathBasedGraph<P, V, R>): PathBasedGraph<P, V, R> {
        return edgesByPathPair.entries.parallelStream()
                .map { entry: Map.Entry<Pair<P, P>, E> -> function.invoke(entry.value) }
                .reduce(DefaultTwoToOnePathToEdgePathBasedGraph<P, V, R>(verticesByPath = verticesByPath) as PathBasedGraph<P, V, R>,
                        { cg, pg ->
                            when (cg) {
                                is TwoToOnePathToEdgeGraph -> {
                                    pg.fold({ v: PersistentMap<P, V>, eSingle: PersistentMap<Pair<P, P>, R> ->
                                                cg.putAllVertices(v)
                                                        .putAllEdges(eSingle)
                                            },
                                            { v: PersistentMap<P, V>, eMany: PersistentMap<Pair<P, P>, PersistentSet<R>> -> // Any output of persistent graph that has
                                                // two to many mapping means the current graph type must be flipped to two-to-many edge map type
                                                // in order to avoid any loss of information
                                                DefaultTwoToManyEdgePathBasedGraph<P, V, R>(verticesByPath = cg.verticesByPath).putAllEdges(cg.edgesByPathPair)
                                                        .putAllVertices(v)
                                                        .putAllEdgeSets(eMany)
                                            })
                                }
                                is TwoToManyPathToEdgeGraph -> {
                                    pg.fold({ v: PersistentMap<P, V>, eSingle: PersistentMap<Pair<P, P>, R> ->
                                                cg.putAllVertices(v)
                                                        .putAllEdges(eSingle)
                                            },
                                            { v: PersistentMap<P, V>, eMany: PersistentMap<Pair<P, P>, PersistentSet<R>> ->
                                                cg.putAllVertices(v)
                                                        .putAllEdgeSets(eMany)
                                            })
                                }
                            }
                        },
                        { pg1, pg2 ->
                            when (pg1) {
                                is TwoToOnePathToEdgeGraph -> {
                                    pg2.fold({ v: PersistentMap<P, V>, eSingle: PersistentMap<Pair<P, P>, R> ->
                                                 pg1.putAllVertices(v)
                                                         .putAllEdges(eSingle)
                                             },
                                             { v: PersistentMap<P, V>, eMany: PersistentMap<Pair<P, P>, PersistentSet<R>> ->
                                                 DefaultTwoToManyEdgePathBasedGraph<P, V, R>(verticesByPath = pg1.verticesByPath).putAllEdges(pg1.edgesByPathPair)
                                                         .putAllVertices(v)
                                                         .putAllEdgeSets(eMany)
                                             })
                                }
                                is TwoToManyPathToEdgeGraph -> {
                                    pg2.fold({ v: PersistentMap<P, V>, eSingle: PersistentMap<Pair<P, P>, R> ->
                                                 pg1.putAllVertices(v)
                                                         .putAllEdges(eSingle)
                                             },
                                             { v: PersistentMap<P, V>, eMany: PersistentMap<Pair<P, P>, PersistentSet<R>> ->
                                                 pg1.putAllVertices(v)
                                                         .putAllEdgeSets(eMany)
                                             })
                                }
                            }
                        })

    }

    override fun hasCycles(): Boolean {
        return edgesByPathPair.stream()
                .parallel()
                .map { entry: Map.Entry<Pair<P, P>, E> -> entry.key.swap() }
                .anyMatch { pair: Pair<P, P> -> edgesByPathPair.containsKey(pair) }
    }

    override fun getCycles(): Stream<Pair<Triple<P, P, E>, Triple<P, P, E>>> {
        return edgesByPathPair.stream()
                .parallel()
                .filter { entry: Map.Entry<Pair<P, P>, E> -> edgesByPathPair.containsKey(entry.key.swap()) }
                .map { entry: Map.Entry<Pair<P, P>, E> ->
                    Pair(Triple(entry.key.first,
                                entry.key.second,
                                entry.value),
                         Triple(entry.key.second,
                                entry.key.first,
                                edgesByPathPair[Pair(entry.key.second,
                                                     entry.key.first)]!!))
                }
    }

    override fun createMinimumSpanningTreeGraphUsingEdgeCostFunction(costComparator: Comparator<E>): PathBasedGraph<P, V, E> {
        return edgesByPathPair.asSequence()
                .sortedWith { e1, e2 ->
                    costComparator.compare(e1.value,
                                           e2.value)
                }
                .fold(UnionFindTree.empty<P>() to persistentMapOf<Pair<P, P>, E>()) { acc, entry ->
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
                    DefaultTwoToOnePathToEdgePathBasedGraph(verticesByPath = verticesByPath,
                                                            edgesByPathPair = pair.second)
                }

    }

    override fun depthFirstSearchOnPath(path: P): Stream<Tuple5<V, P, E, P, V>> {
        return path.some()
                .filter { p: P -> verticesByPath.containsKey(p) }
                .map { p: P -> Stream.of(p) }
                .getOrElse { Stream.empty() }
                .flatMap { p: P ->
                    StreamSupport.stream(TwoToOneEdgeDepthFirstSearchSpliterator<P, V, E>(inputPath = p,
                                                                                          verticesByPath = verticesByPath,
                                                                                          edgesByPathPair = edgesByPathPair,
                                                                                          pathConnections = pathConnections),
                                         false)
                }
    }
}