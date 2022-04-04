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

        private fun <P, V, E> Stream<PathBasedGraph<P, V, E>>.reduceByMonoid(startValue: PathBasedGraph<P, V, E> = PathBasedGraph.monoid<P, V, E>()
                .empty()): PathBasedGraph<P, V, E> {
            val monoid = PathBasedGraph.monoid<P, V, E>()
            return this.reduce(startValue,
                               monoid,
                               monoid)
        }

        private fun <K, V> PersistentMap<K, PersistentSet<V>>.combine(otherMap: PersistentMap<K, PersistentSet<V>>): PersistentMap<K, PersistentSet<V>> {
            return when {
                this.isEmpty() -> {
                    otherMap
                }
                otherMap.isEmpty() -> {
                    this
                }
                this.size > otherMap.size -> {
                    val finalResultHolder: Array<PersistentMap<K, PersistentSet<V>>> = arrayOf(this)
                    otherMap.forEach({ (key, value) ->
                                         finalResultHolder[0] = finalResultHolder[0].put(key,
                                                                                         finalResultHolder[0].getOrDefault(key,
                                                                                                                           persistentSetOf())
                                                                                                 .addAll(value))
                                     })
                    finalResultHolder[0]
                }
                else -> {
                    val finalResultHolder: Array<PersistentMap<K, PersistentSet<V>>> = arrayOf(otherMap)
                    this.forEach({ (key, value) ->
                                     finalResultHolder[0] = finalResultHolder[0].put(key,
                                                                                     finalResultHolder[0].getOrDefault(key,
                                                                                                                       persistentSetOf())
                                                                                             .addAll(value))
                                 })
                    finalResultHolder[0]
                }
            }
        }

        private fun <K, V> Stream<Pair<K, V>>.reducePairsToPersistentSetValueMap(startValue: PersistentMap<K, PersistentSet<V>> = persistentMapOf(),
                                                                                 filter: (K) -> Boolean = { true }): PersistentMap<K, PersistentSet<V>> {
            return this.reduce(startValue,
                               { pm, pair ->
                                   if (filter.invoke(pair.first)) {
                                       pm.put(pair.first,
                                              pm.getOrDefault(pair.first,
                                                              persistentSetOf())
                                                      .add(pair.second))
                                   } else {
                                       pm
                                   }
                               },
                               { pm1, pm2 ->
                                   pm1.combine(pm2)
                               })
        }

        private fun <K, V> Stream<Map.Entry<K, V>>.reduceEntriesToPersistentSetValueMap(startValue: PersistentMap<K, PersistentSet<V>> = persistentMapOf(),
                                                                                        filter: (K) -> Boolean = { true }): PersistentMap<K, PersistentSet<V>> {
            return this.reduce(startValue,
                               { pm, entry ->
                                   if (filter.invoke(entry.key)) {
                                       pm.put(entry.key,
                                              pm.getOrDefault(entry.key,
                                                              persistentSetOf())
                                                      .add(entry.value))
                                   } else {
                                       pm
                                   }
                               },
                               { pm1, pm2 ->
                                   pm1.combine(pm2)
                               })
        }

    }

    override val vertices: PersistentList<V> by lazy {
        verticesByPath.stream()
                .parallel()
                .map { e -> e.value }
                .reduce(persistentListOf<V>(),
                        { pl, v -> pl.add(v) },
                        { pl1, pl2 -> pl1.addAll(pl2) })
    }

    override val edgesByConnectedVertices: PersistentMap<Pair<V, V>, PersistentSet<E>> by lazy {
        edgesByPathPair.stream()
                .parallel()
                .map { e: Map.Entry<Pair<P, P>, E> ->
                    verticesByPath[e.key.first].toOption()
                            .zip(verticesByPath[e.key.second].toOption()) { v1: V, v2: V ->
                                (v1 to v2) to e.value
                            }
                }
                .flatMap { opt -> opt.stream() }
                .reducePairsToPersistentSetValueMap()

    }

    /**
     * Lazily initialized path_connections map: e.g.
     * { ( parent_path_0 to child_path_0 ), ... ( parent_path_n-1 to child_path_n-1 ) }
     * detailing what "child" paths each path has if directionality is being
     * used in the algorithm in question
     */
    private val pathConnections: ImmutableMap<P, ImmutableSet<P>> by lazy {
        edgesByPathPair.keys.stream()
                .parallel()
                .reducePairsToPersistentSetValueMap()
    }

    override fun edgeCount(): Int {
        return edgesByPathPair.size
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

    override fun <R, M : Map<out P, @UnsafeVariance R>> flatMapVertices(function: (P, V) -> M): PathBasedGraph<P, R, E> {
        val updatedVertices = verticesByPath.stream()
                .parallel()
                .map { (key, value): Map.Entry<P, V> ->
                    function.invoke(key,
                                    value)
                }
                .flatMap { vertexMap: M -> vertexMap.entries.parallelStream() }
                .map { entry -> entry.key to entry.value }
                .reduce(persistentMapOf<P, R>(),
                        { pm, vPair ->
                            pm.put(vPair.first,
                                   vPair.second)
                        },
                        { pm1, pm2 -> pm1.putAll(pm2) })
        return DefaultTwoToOnePathToEdgePathBasedGraph<P, R, E>(verticesByPath = updatedVertices).putAllEdges(edgesByPathPair)
    }

    override fun <R, M : Map<out Pair<P, P>, @UnsafeVariance R>> flatMapEdges(function: (Pair<P, P>, E) -> M): PathBasedGraph<P, V, R> {
        val updatedEdges = edgesByPathPair.entries.parallelStream()
                .map { (key, value) ->
                    function.invoke(key,
                                    value)
                }
                .flatMap { edgesMap: M -> edgesMap.entries.parallelStream() }
                .map { (key, value) -> key to value }
                .reduce(persistentMapOf<Pair<P, P>, R>(),
                        { pm, ePair ->
                            pm.put(ePair.first,
                                   ePair.second)
                        },
                        { pm1, pm2 -> pm1.putAll(pm2) })
        return DefaultTwoToOnePathToEdgePathBasedGraph<P, V, R>(verticesByPath = verticesByPath).putAllEdges(updatedEdges)
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