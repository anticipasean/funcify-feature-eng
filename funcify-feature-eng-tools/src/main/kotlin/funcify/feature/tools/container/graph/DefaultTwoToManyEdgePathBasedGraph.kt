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

internal data class DefaultTwoToManyEdgePathBasedGraph<P, V, E>(override val verticesByPath: PersistentMap<P, V> = persistentMapOf(),
                                                                override val edgesSetByPathPair: PersistentMap<Pair<P, P>, PersistentSet<E>> = persistentMapOf()) : TwoToManyPathToEdgeGraph<P, V, E> {

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
        edgesSetByPathPair.stream()
                .map { e: Map.Entry<Pair<P, P>, PersistentSet<E>> ->
                    verticesByPath[e.key.first].toOption()
                            .zip(verticesByPath[e.key.second].toOption()) { v1: V, v2: V ->
                                e.value.fold(persistentSetOf<Triple<V, V, E>>()) { set, edge ->
                                    set.add(Triple(v1,
                                                   v2,
                                                   edge))
                                }
                            }
                            .getOrElse { persistentSetOf() }
                }
                .flatMap { tSet: PersistentSet<Triple<V, V, E>> -> tSet.stream() }
                .reduce(persistentMapOf(),
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
        edgesSetByPathPair.keys.stream()
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
        return edgesSetByPathPair.stream()
                .flatMap { e -> e.value.stream() }
    }

    override fun connectedPaths(): Stream<Pair<P, P>> {
        return edgesSetByPathPair.keys.stream()
    }

    override fun getVertex(path: P): Option<V> {
        return verticesByPath[path].toOption()
    }

    override fun putVertex(path: P,
                           vertex: V): PathBasedGraph<P, V, E> {
        return DefaultTwoToManyEdgePathBasedGraph(verticesByPath = verticesByPath.put(path,
                                                                                      vertex),
                                                  edgesSetByPathPair = edgesSetByPathPair)
    }

    override fun <M : Map<out P, V>> putAllVertices(vertices: M): PathBasedGraph<P, V, E> {
        return DefaultTwoToManyEdgePathBasedGraph(verticesByPath = verticesByPath.putAll(vertices),
                                                  edgesSetByPathPair = edgesSetByPathPair)
    }

    /**
     * Do not add an edge that does not have a corresponding vertex path
     */
    override fun putEdge(path1: P,
                         path2: P,
                         edge: E): PathBasedGraph<P, V, E> {
        return if (verticesByPath.containsKey(path1) && verticesByPath.containsKey(path2)) {
            val edgeKey = Pair(path1,
                               path2)
            DefaultTwoToManyEdgePathBasedGraph(verticesByPath = verticesByPath,
                                               edgesSetByPathPair = edgesSetByPathPair.put(edgeKey,
                                                                                           edgesSetByPathPair.getOrDefault(edgeKey,
                                                                                                                           persistentSetOf())
                                                                                                   .add(edge)))
        } else {
            this
        }
    }

    override fun putEdge(connectedPaths: Pair<P, P>,
                         edge: E): PathBasedGraph<P, V, E> {
        return if (verticesByPath.containsKey(connectedPaths.first) && verticesByPath.containsKey(connectedPaths.second)) {
            DefaultTwoToManyEdgePathBasedGraph(verticesByPath = verticesByPath,
                                               edgesSetByPathPair = edgesSetByPathPair.put(connectedPaths,
                                                                                           edgesSetByPathPair.getOrDefault(connectedPaths,
                                                                                                                           persistentSetOf())
                                                                                                   .add(edge)))
        } else {
            this
        }
    }

    override fun <M : Map<Pair<P, P>, E>> putAllEdges(edges: M): PathBasedGraph<P, V, E> {
        return DefaultTwoToManyEdgePathBasedGraph(verticesByPath = verticesByPath,
                                                  edgesSetByPathPair = edges.entries.parallelStream()
                                                          .reduce(edgesSetByPathPair,
                                                                  { esm, entry ->
                                                                      if (verticesByPath.containsKey(entry.key.first) && verticesByPath.containsKey(entry.key.second)) {
                                                                          esm.put(entry.key,
                                                                                  esm.getOrDefault(entry.key,
                                                                                                   persistentSetOf())
                                                                                          .add(entry.value))
                                                                      } else {
                                                                          esm
                                                                      }
                                                                  },
                                                                  { esm1, esm2 ->
                                                                      esm2.forEach({ (key, value) ->
                                                                                       esm1.put(key,
                                                                                                esm1.getOrDefault(key,
                                                                                                                  persistentSetOf())
                                                                                                        .addAll(value))
                                                                                   })
                                                                      esm1
                                                                  }))
    }

    override fun <S : Set<@UnsafeVariance E>, M : Map<Pair<P, P>, S>> putAllEdgeSets(edges: M): PathBasedGraph<P, V, E> {
        return DefaultTwoToManyEdgePathBasedGraph(verticesByPath = verticesByPath,
                                                  edgesSetByPathPair = edges.entries.parallelStream()
                                                          .flatMap { entry ->
                                                              entry.value.parallelStream()
                                                                      .map { edge -> entry.key to edge }
                                                          }
                                                          .reduce(edgesSetByPathPair,
                                                                  { esm, entry ->
                                                                      if (verticesByPath.containsKey(entry.first.first) && verticesByPath.containsKey(entry.first.second)) {
                                                                          esm.put(entry.first,
                                                                                  esm.getOrDefault(entry.first,
                                                                                                   persistentSetOf())
                                                                                          .add(entry.second))
                                                                      } else {
                                                                          esm
                                                                      }
                                                                  },
                                                                  { esm1, esm2 ->
                                                                      esm2.forEach({ (key, value) ->
                                                                                       esm1.put(key,
                                                                                                esm1.getOrDefault(key,
                                                                                                                  persistentSetOf())
                                                                                                        .addAll(value))
                                                                                   })
                                                                      esm1
                                                                  }))
    }

    override fun getEdgesFromPathToPath(path1: P,
                                        path2: P): ImmutableSet<E> {
        return edgesSetByPathPair.getOrDefault(Pair(path1,
                                                    path2),
                                               persistentSetOf())

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
                .map { pair: Pair<P, P> ->
                    edgesSetByPathPair[pair].toOption()
                            .getOrElse { persistentSetOf() }
                }
                .flatMap { eSet: PersistentSet<E> ->
                    eSet.stream()
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
                .map { pair: Pair<P, P> ->
                    edgesSetByPathPair[pair].toOption()
                            .getOrElse { persistentSetOf() }
                }
                .flatMap { eSet: PersistentSet<E> ->
                    eSet.stream()
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
        val updatedVertices = verticesByPath.asSequence()
                .filter { entry: Map.Entry<P, V> -> function.invoke(entry.value) }
                .fold(persistentMapOf<P, V>()) { acc, entry ->
                    acc.put(entry.key,
                            entry.value)
                }
        val updatedEdges = edgesSetByPathPair.asSequence()
                .filter { entry: Map.Entry<Pair<P, P>, PersistentSet<E>> ->
                    sequenceOf(entry.key.first,
                               entry.key.second).all { p ->
                        updatedVertices.containsKey(p)
                    }
                }
                .fold(persistentMapOf<Pair<P, P>, PersistentSet<E>>()) { acc, entry ->
                    acc.put(entry.key,
                            entry.value)
                }
        return DefaultTwoToManyEdgePathBasedGraph(updatedVertices,
                                                  updatedEdges)
    }

    override fun filterEdges(function: (E) -> Boolean): PathBasedGraph<P, V, E> {
        val updatedEdges = edgesSetByPathPair.asSequence()
                .flatMap { entry: Map.Entry<Pair<P, P>, PersistentSet<E>> ->
                    entry.value.asSequence()
                            .map { e -> entry.key to e }
                }
                .filter { entry: Pair<Pair<P, P>, E> ->
                    function.invoke(entry.second)
                }
                .fold(persistentMapOf<Pair<P, P>, PersistentSet<E>>()) { acc, entry ->
                    acc.put(entry.first,
                            acc.getOrDefault(entry.first,
                                             persistentSetOf())
                                    .add(entry.second))
                }
        return DefaultTwoToManyEdgePathBasedGraph(verticesByPath = verticesByPath,
                                                  edgesSetByPathPair = updatedEdges)
    }

    override fun <R> mapVertices(function: (V) -> R): PathBasedGraph<P, R, E> {
        val updatedVertices = verticesByPath.stream()
                .map { entry: Map.Entry<P, V> -> entry.key to function.invoke(entry.value) }
                .reduce(persistentMapOf<P, R>(),
                        { vm, vpair ->
                            vm.put(vpair.first,
                                   vpair.second)
                        },
                        { vm1, vm2 -> vm1.putAll(vm2) })
        return DefaultTwoToManyEdgePathBasedGraph(verticesByPath = updatedVertices,
                                                  edgesSetByPathPair = edgesSetByPathPair)
    }

    override fun <R> mapEdges(function: (E) -> R): PathBasedGraph<P, V, R> {
        val updatedEdges = edgesSetByPathPair.stream()
                .map { entry: Map.Entry<Pair<P, P>, PersistentSet<E>> ->
                    entry.key to entry.value.parallelStream()
                            .map { e -> function.invoke(e) }
                            .reduce(persistentSetOf<R>(),
                                    { set, e -> set.add(e) },
                                    { s1, s2 -> s1.addAll(s2) })
                }
                .reduce(persistentMapOf<Pair<P, P>, PersistentSet<R>>(),
                        { pm, ePair ->
                            pm.put(ePair.first,
                                   pm.getOrDefault(ePair.first,
                                                   persistentSetOf())
                                           .addAll(ePair.second))
                        },
                        { pm1, pm2 ->
                            pm2.forEach({ (key, value) ->
                                            pm1.put(key,
                                                    pm1.getOrDefault(key,
                                                                     persistentSetOf())
                                                            .addAll(value))
                                        })
                            pm1
                        })
        return DefaultTwoToManyEdgePathBasedGraph(verticesByPath = verticesByPath,
                                                  edgesSetByPathPair = updatedEdges)
    }

    override fun <R> flatMapVertices(function: (V) -> PathBasedGraph<P, R, E>): PathBasedGraph<P, R, E> {
        return verticesByPath.asSequence()
                .map { entry: Map.Entry<P, V> -> function.invoke(entry.value) }
                .fold(DefaultTwoToManyEdgePathBasedGraph<P, R, E>() as PathBasedGraph<P, R, E>) { currentGraph: PathBasedGraph<P, R, E>, pg: PathBasedGraph<P, R, E> ->
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


    override fun <R> flatMapEdges(function: (E) -> PathBasedGraph<P, V, R>): PathBasedGraph<P, V, R> {
        return edgesSetByPathPair.entries.parallelStream()
                .flatMap { entry: Map.Entry<Pair<P, P>, PersistentSet<E>> ->
                    entry.value.parallelStream()
                            .map { e -> function.invoke(e) }
                }
                .reduce(DefaultTwoToManyEdgePathBasedGraph<P, V, R>(verticesByPath = verticesByPath) as PathBasedGraph<P, V, R>,
                        { cg, pg ->
                            pg.fold({ v: PersistentMap<P, V>, eSingle: PersistentMap<Pair<P, P>, R> ->
                                        cg.putAllVertices(v)
                                                .putAllEdges(eSingle)
                                    },
                                    { v: PersistentMap<P, V>, eMany: PersistentMap<Pair<P, P>, PersistentSet<R>> ->
                                        cg.putAllVertices(v)
                                                .putAllEdgeSets(eMany)
                                    })
                        },
                        { pg1, pg2 ->
                            pg2.fold({ v: PersistentMap<P, V>, eSingle: PersistentMap<Pair<P, P>, R> ->
                                         pg1.putAllVertices(v)
                                                 .putAllEdges(eSingle)
                                     },
                                     { v: PersistentMap<P, V>, eMany: PersistentMap<Pair<P, P>, PersistentSet<R>> ->
                                         pg1.putAllVertices(v)
                                                 .putAllEdgeSets(eMany)
                                     })
                        })


    }

    override fun hasCycles(): Boolean {
        return edgesSetByPathPair.stream()
                .map { entry: Map.Entry<Pair<P, P>, PersistentSet<E>> -> entry.key.swap() }
                .anyMatch { pair: Pair<P, P> -> edgesSetByPathPair.containsKey(pair) }
    }

    override fun getCycles(): Stream<Pair<Triple<P, P, E>, Triple<P, P, E>>> {
        return edgesSetByPathPair.stream()
                .filter { entry: Map.Entry<Pair<P, P>, PersistentSet<E>> -> edgesSetByPathPair.containsKey(entry.key.swap()) }
                .flatMap { entry: Map.Entry<Pair<P, P>, PersistentSet<E>> ->
                    edgesSetByPathPair.getOrDefault(entry.key,
                                                    persistentSetOf())
                            .stream()
                            .map { e ->
                                Triple(entry.key.first,
                                       entry.key.second,
                                       e)
                            }
                            .flatMap { triple ->
                                edgesSetByPathPair.getOrDefault(entry.key.swap(),
                                                                persistentSetOf())
                                        .stream()
                                        .map { e ->
                                            triple to Triple(entry.key.first,
                                                             entry.key.second,
                                                             e)
                                        }
                            }
                }
    }

    override fun createMinimumSpanningTreeGraphUsingEdgeCostFunction(costComparator: Comparator<E>): PathBasedGraph<P, V, E> {
        return edgesSetByPathPair.asSequence()
                .flatMap { entry ->
                    entry.value.asSequence()
                            .map { e -> entry.key to e }
                }
                .sortedWith { e1, e2 ->
                    costComparator.compare(e1.second,
                                           e2.second)
                }
                .fold(UnionFindTree.empty<P>() to persistentMapOf<Pair<P, P>, E>()) { acc, entry ->
                    val root1AndUnionFindTree: Pair<Option<P>, UnionFindTree<P>> = acc.first.add(entry.first.first)
                            .add(entry.first.second)
                            .find(entry.first.first)
                    val root2AndUnionFindTree: Pair<Option<P>, UnionFindTree<P>> = root1AndUnionFindTree.second.find(entry.first.second)
                    val rootPath1: Option<P> = root1AndUnionFindTree.first
                    val rootPath2: Option<P> = root2AndUnionFindTree.first
                    if (rootPath1.filter({ r1 ->
                                             rootPath2.filter { r2 -> r1 != r2 }
                                                     .isDefined()
                                         })
                                    .isDefined()) {
                        root2AndUnionFindTree.second.union(rootPath1.orNull()!!,
                                                           rootPath2.orNull()!!) to acc.second.put(entry.first,
                                                                                                   entry.second)
                    } else {
                        root2AndUnionFindTree.second to acc.second
                    }
                }
                .let { pair: Pair<UnionFindTree<P>, PersistentMap<Pair<P, P>, E>> ->
                    DefaultTwoToManyEdgePathBasedGraph(verticesByPath = verticesByPath,
                                                       edgesSetByPathPair = pair.second.stream()
                                                               .reduce(persistentMapOf<Pair<P, P>, PersistentSet<E>>(),
                                                                       { pm, entry ->
                                                                           pm.put(entry.key,
                                                                                  pm.getOrDefault(entry.key,
                                                                                                  persistentSetOf())
                                                                                          .add(entry.value))
                                                                       },
                                                                       { pm1, pm2 ->
                                                                           pm2.forEach({ (key, value) ->
                                                                                           pm1.put(key,
                                                                                                   pm1.getOrDefault(key,
                                                                                                                    persistentSetOf())
                                                                                                           .addAll(value))
                                                                                       })
                                                                           pm1
                                                                       }))
                }

    }

    override fun depthFirstSearchOnPath(path: P): Stream<Tuple5<V, P, E, P, V>> {
        return path.some()
                .filter { p: P -> verticesByPath.containsKey(p) }
                .map { p: P -> Stream.of(p) }
                .getOrElse { Stream.empty() }
                .flatMap { p: P ->
                    StreamSupport.stream(TwoToManyEdgeDepthFirstSearchSpliterator<P, V, E>(inputPath = p,
                                                                                           verticesByPath = verticesByPath,
                                                                                           edgesSetByPathPair = edgesSetByPathPair,
                                                                                           pathConnections = pathConnections),
                                         false)
                }
    }
}