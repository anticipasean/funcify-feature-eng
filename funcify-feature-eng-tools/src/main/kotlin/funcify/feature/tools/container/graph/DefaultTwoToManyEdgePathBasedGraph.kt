package funcify.feature.tools.container.graph

import arrow.core.Option
import arrow.core.Tuple5
import arrow.core.getOrElse
import arrow.core.identity
import arrow.core.some
import arrow.core.toOption
import funcify.feature.tools.container.tree.UnionFindTree
import funcify.feature.tools.extensions.OptionExtensions.stream
import funcify.feature.tools.extensions.PersistentListExtensions.reduceToPersistentList
import funcify.feature.tools.extensions.PersistentMapExtensions.reduceEntriesToPersistentMap
import funcify.feature.tools.extensions.PersistentMapExtensions.reduceEntriesToPersistentSetValueMap
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentSetValueMap
import funcify.feature.tools.extensions.StreamExtensions.flatMapOptions
import java.util.stream.Stream
import java.util.stream.Stream.empty
import java.util.stream.StreamSupport
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

internal data class DefaultTwoToManyEdgePathBasedGraph<P, V, E>(
    override val verticesByPath: PersistentMap<P, V> = persistentMapOf(),
    override val edgesSetByPathPair: PersistentMap<Pair<P, P>, PersistentSet<E>> = persistentMapOf()
) : TwoToManyPathToEdgeGraph<P, V, E> {

    companion object {

        private fun <T1, T2> Pair<T1, T2>.swap(): Pair<T2, T1> {
            return Pair(this.second, this.first)
        }

        private fun <K, V> ImmutableMap<K, V>.stream(): Stream<Map.Entry<K, V>> {
            return this.entries.stream()
        }
    }

    override val vertices: PersistentList<V> by lazy {
        verticesByPath.stream().parallel().map { e -> e.value }.reduceToPersistentList()
    }

    override val edgesByConnectedVertices: PersistentMap<Pair<V, V>, PersistentSet<E>> by lazy {
        edgesSetByPathPair
            .stream()
            .parallel()
            .map { e: Map.Entry<Pair<P, P>, PersistentSet<E>> ->
                verticesByPath[e.key.first].toOption().zip(
                    verticesByPath[e.key.second].toOption()
                ) { v1: V, v2: V -> (v1 to v2) to e.value }
            }
            .flatMapOptions()
            .flatMap { pair: Pair<Pair<V, V>, PersistentSet<E>> ->
                pair.second.stream().map { e -> pair.first to e }
            }
            .reducePairsToPersistentSetValueMap()
    }

    /**
     * Lazily initialized path_connections map: e.g. { ( parent_path_0 to child_path_0 ), ... (
     * parent_path_n-1 to child_path_n-1 ) } detailing what "child" paths each path has if
     * directionality is being used in the algorithm in question
     */
    private val forwardConnections: ImmutableMap<P, ImmutableSet<P>> by lazy {
        edgesSetByPathPair.keys.stream().parallel().reducePairsToPersistentSetValueMap()
    }

    private val reverseConnections: ImmutableMap<P, ImmutableSet<P>> by lazy {
        edgesSetByPathPair.keys
            .stream()
            .parallel()
            .map { pair -> pair.swap() }
            .reducePairsToPersistentSetValueMap()
    }

    private val lazyEdgeCount: Int by lazy {
        edgesSetByPathPair.values.parallelStream().mapToInt { set -> set.size }.sum()
    }

    override fun edgeCount(): Int {
        return lazyEdgeCount
    }

    override fun edgesAsStream(): Stream<E> {
        return edgesSetByPathPair.stream().flatMap { e -> e.value.stream() }
    }

    override fun connectedPaths(): Stream<Pair<P, P>> {
        return edgesSetByPathPair.keys.stream()
    }

    override fun getVertex(path: P): Option<V> {
        return verticesByPath[path].toOption()
    }

    override fun putVertex(path: P, vertex: V): PathBasedGraph<P, V, E> {
        return DefaultTwoToManyEdgePathBasedGraph(
            verticesByPath = verticesByPath.put(path, vertex),
            edgesSetByPathPair = edgesSetByPathPair
        )
    }

    override fun <M : Map<out P, @UnsafeVariance V>> putAllVertices(
        vertices: M
    ): PathBasedGraph<P, V, E> {
        return DefaultTwoToManyEdgePathBasedGraph(
            verticesByPath = verticesByPath.putAll(vertices),
            edgesSetByPathPair = edgesSetByPathPair
        )
    }

    override fun removeVertex(path: P): PathBasedGraph<P, V, E> {
        return if (path in verticesByPath) {
            DefaultTwoToManyEdgePathBasedGraph<P, V, E>(
                    verticesByPath = verticesByPath.remove(path)
                )
                .putAllEdgeSets(edgesSetByPathPair)
        } else {
            this
        }
    }

    override fun <S : Set<P>> removeAllVertices(paths: S): PathBasedGraph<P, V, E> {
        return DefaultTwoToManyEdgePathBasedGraph<P, V, E>(
                verticesByPath =
                    paths
                        .parallelStream()
                        .reduce(
                            verticesByPath,
                            { v, p -> v.remove(p) },
                            { v1, v2 -> v1.putAll(v2) }
                        )
            )
            .putAllEdgeSets(edgesSetByPathPair)
    }

    /** Do not add an edge that does not have a corresponding vertex path */
    override fun putEdge(path1: P, path2: P, edge: E): PathBasedGraph<P, V, E> {
        return if (verticesByPath.containsKey(path1) && verticesByPath.containsKey(path2)) {
            val edgeKey = Pair(path1, path2)
            DefaultTwoToManyEdgePathBasedGraph(
                verticesByPath = verticesByPath,
                edgesSetByPathPair =
                    edgesSetByPathPair.put(
                        edgeKey,
                        edgesSetByPathPair.getOrDefault(edgeKey, persistentSetOf()).add(edge)
                    )
            )
        } else {
            this
        }
    }

    override fun putEdge(connectedPaths: Pair<P, P>, edge: E): PathBasedGraph<P, V, E> {
        return if (
            verticesByPath.containsKey(connectedPaths.first) &&
                verticesByPath.containsKey(connectedPaths.second)
        ) {
            DefaultTwoToManyEdgePathBasedGraph(
                verticesByPath = verticesByPath,
                edgesSetByPathPair =
                    edgesSetByPathPair.put(
                        connectedPaths,
                        edgesSetByPathPair.getOrDefault(connectedPaths, persistentSetOf()).add(edge)
                    )
            )
        } else {
            this
        }
    }

    override fun <M : Map<out Pair<P, P>, @UnsafeVariance E>> putAllEdges(
        edges: M
    ): PathBasedGraph<P, V, E> {
        val updatedEdges =
            edges.entries
                .parallelStream()
                .filter { entry: Map.Entry<Pair<P, P>, E> ->
                    verticesByPath.containsKey(entry.key.first) &&
                        verticesByPath.containsKey(entry.key.second)
                }
                .reduceEntriesToPersistentSetValueMap(startValue = edgesSetByPathPair)
        return DefaultTwoToManyEdgePathBasedGraph(
            verticesByPath = verticesByPath,
            edgesSetByPathPair = updatedEdges
        )
    }

    override fun <S : Set<@UnsafeVariance E>, M : Map<out Pair<P, P>, S>> putAllEdgeSets(
        edges: M
    ): PathBasedGraph<P, V, E> {
        val updatedEdgeSets =
            edges.entries
                .parallelStream()
                .flatMap { entry -> entry.value.parallelStream().map { edge -> entry.key to edge } }
                .filter { pair: Pair<Pair<P, P>, E> ->
                    verticesByPath.containsKey(pair.first.first) &&
                        verticesByPath.containsKey(pair.first.second)
                }
                .reducePairsToPersistentSetValueMap(startValue = edgesSetByPathPair)
        return DefaultTwoToManyEdgePathBasedGraph(
            verticesByPath = verticesByPath,
            edgesSetByPathPair = updatedEdgeSets
        )
    }

    override fun removeEdges(connectedPaths: Pair<P, P>): PathBasedGraph<P, V, E> {
        return DefaultTwoToManyEdgePathBasedGraph(
            verticesByPath = verticesByPath,
            edgesSetByPathPair = edgesSetByPathPair.remove(connectedPaths)
        )
    }

    override fun <S : Set<Pair<P, P>>> removeAllEdges(connectedPaths: S): PathBasedGraph<P, V, E> {
        return DefaultTwoToManyEdgePathBasedGraph(
            verticesByPath = verticesByPath,
            edgesSetByPathPair =
                edgesSetByPathPair.entries
                    .parallelStream()
                    .filter { (edgeId, _) -> edgeId !in connectedPaths }
                    .reduceEntriesToPersistentMap()
        )
    }

    override fun getEdgesFromPathToPath(path1: P, path2: P): ImmutableSet<E> {
        return edgesSetByPathPair.getOrDefault(Pair(path1, path2), persistentSetOf())
    }

    override fun getEdgesFromPathToPath(connectedPaths: Pair<P, P>): ImmutableSet<E> {
        return edgesSetByPathPair.getOrDefault(connectedPaths, persistentSetOf())
    }

    /**
     * Overrides default implementation making use of lazily initiated path_connections map, which
     * should be faster with larger graphs since not all path connections have to be streamed
     * through
     */
    override fun getEdgesFrom(path: P): Stream<E> {
        return forwardConnections[path]
            .toOption()
            .map { ps: ImmutableSet<P> -> ps.stream().map { p: P -> path to p } }
            .getOrElse { Stream.empty() }
            .map { pair: Pair<P, P> ->
                edgesSetByPathPair[pair].toOption().getOrElse { persistentSetOf() }
            }
            .flatMap { eSet: PersistentSet<E> -> eSet.stream() }
    }

    /**
     * Overrides default implementation making use of lazily initiated path_connections map, which
     * should be faster with larger graphs since not all path connections have to be streamed
     * through
     */
    override fun getEdgesTo(path: P): Stream<E> {
        return reverseConnections[path]
            .toOption()
            .map(ImmutableSet<P>::stream)
            .fold(::empty, ::identity)
            .map { p -> p to path }
            .map { pair -> edgesSetByPathPair[pair].toOption() }
            .flatMapOptions()
            .flatMap(ImmutableSet<E>::stream)
    }

    override fun successors(vertexPath: P): Stream<out Pair<P, V>> {
        return forwardConnections[vertexPath]
            .toOption()
            .getOrElse { persistentSetOf() }
            .stream()
            .filter { p: P -> p != vertexPath }
            .flatMap { p: P -> getVertex(p).map({ v -> p to v }).stream() }
    }

    override fun successors(vertex: V, pathExtractor: Function1<V, P>): Stream<out Pair<P, V>> {
        return successors(pathExtractor.invoke(vertex))
    }

    override fun predecessors(vertexPath: P): Stream<out Pair<P, V>> {
        return reverseConnections[vertexPath]
            .toOption()
            .map(ImmutableSet<P>::stream)
            .fold(::empty, ::identity)
            .map { p -> verticesByPath[p].toOption().map { v -> p to v } }
            .flatMapOptions()
    }

    override fun predecessors(vertex: V, pathExtractor: Function1<V, P>): Stream<out Pair<P, V>> {
        return predecessors(pathExtractor.invoke(vertex))
    }

    override fun adjacentVertices(vertexPath: P): Stream<Pair<P, V>> {
        return Stream.concat(predecessors(vertexPath), successors(vertexPath))
    }

    override fun adjacentVertices(vertex: V, pathExtractor: (V) -> P): Stream<out Pair<P, V>> {
        return adjacentVertices(pathExtractor.invoke(vertex))
    }

    override fun filterVertices(function: (V) -> Boolean): PathBasedGraph<P, V, E> {
        val updatedVertices =
            verticesByPath
                .stream()
                .parallel()
                .filter { entry: Map.Entry<P, V> -> function.invoke(entry.value) }
                .reduceEntriesToPersistentMap()
        val updatedEdges =
            edgesSetByPathPair
                .stream()
                .parallel()
                .filter { entry: Map.Entry<Pair<P, P>, PersistentSet<E>> ->
                    sequenceOf(entry.key.first, entry.key.second).all { p ->
                        updatedVertices.containsKey(p)
                    }
                }
                .reduceEntriesToPersistentMap()
        return DefaultTwoToManyEdgePathBasedGraph(updatedVertices, updatedEdges)
    }

    override fun filterEdges(function: (E) -> Boolean): PathBasedGraph<P, V, E> {
        val updatedEdges =
            edgesSetByPathPair
                .stream()
                .parallel()
                .flatMap { entry: Map.Entry<Pair<P, P>, PersistentSet<E>> ->
                    entry.value.stream().map { e -> entry.key to e }
                }
                .filter { entry: Pair<Pair<P, P>, E> -> function.invoke(entry.second) }
                .reducePairsToPersistentSetValueMap()
        return DefaultTwoToManyEdgePathBasedGraph(
            verticesByPath = verticesByPath,
            edgesSetByPathPair = updatedEdges
        )
    }

    override fun <R> mapVertices(function: (V) -> R): PathBasedGraph<P, R, E> {
        val updatedVertices =
            verticesByPath
                .stream()
                .parallel()
                .map { entry: Map.Entry<P, V> -> entry.key to function.invoke(entry.value) }
                .reducePairsToPersistentMap()
        return DefaultTwoToManyEdgePathBasedGraph(
            verticesByPath = updatedVertices,
            edgesSetByPathPair = edgesSetByPathPair
        )
    }

    override fun <R> mapEdges(function: (E) -> R): PathBasedGraph<P, V, R> {
        val updatedEdges =
            edgesSetByPathPair
                .stream()
                .parallel()
                .flatMap { entry: Map.Entry<Pair<P, P>, PersistentSet<E>> ->
                    entry.value.parallelStream().map { e -> entry.key to function.invoke(e) }
                }
                .reducePairsToPersistentSetValueMap()
        return DefaultTwoToManyEdgePathBasedGraph(
            verticesByPath = verticesByPath,
            edgesSetByPathPair = updatedEdges
        )
    }

    override fun <R, M : Map<out P, @UnsafeVariance R>> flatMapVertices(
        function: (P, V) -> M
    ): PathBasedGraph<P, R, E> {
        val updatedVertices =
            verticesByPath
                .stream()
                .parallel()
                .map { (key, value): Map.Entry<P, V> -> function.invoke(key, value) }
                .flatMap { vertexMap: M -> vertexMap.entries.parallelStream() }
                .reduceEntriesToPersistentMap()
        return DefaultTwoToManyEdgePathBasedGraph<P, R, E>(verticesByPath = updatedVertices)
            .putAllEdgeSets(edgesSetByPathPair)
    }

    override fun <R, M : Map<out Pair<P, P>, @UnsafeVariance R>> flatMapEdges(
        function: (Pair<P, P>, E) -> M
    ): PathBasedGraph<P, V, R> {
        val updatedEdges =
            edgesSetByPathPair.entries
                .parallelStream()
                .flatMap { entry -> entry.value.parallelStream().map { e -> entry.key to e } }
                .map { (key, value) -> function.invoke(key, value) }
                .flatMap { edgesMap: M -> edgesMap.entries.parallelStream() }
                .filter { entry: Map.Entry<Pair<P, P>, R> ->
                    verticesByPath.containsKey(entry.key.first) &&
                        verticesByPath.containsKey(entry.key.second)
                }
                .reduceEntriesToPersistentSetValueMap()
        return DefaultTwoToManyEdgePathBasedGraph<P, V, R>(verticesByPath = verticesByPath)
            .putAllEdgeSets(updatedEdges)
    }

    override fun hasCycles(): Boolean {
        return edgesSetByPathPair
            .stream()
            .map { entry: Map.Entry<Pair<P, P>, PersistentSet<E>> -> entry.key.swap() }
            .anyMatch { pair: Pair<P, P> -> edgesSetByPathPair.containsKey(pair) }
    }

    override fun getCycles(): Stream<out Pair<Triple<P, P, E>, Triple<P, P, E>>> {
        return edgesSetByPathPair
            .stream()
            .filter { entry: Map.Entry<Pair<P, P>, PersistentSet<E>> ->
                edgesSetByPathPair.containsKey(entry.key.swap())
            }
            .flatMap { entry: Map.Entry<Pair<P, P>, PersistentSet<E>> ->
                edgesSetByPathPair
                    .getOrDefault(entry.key, persistentSetOf())
                    .stream()
                    .map { e -> Triple(entry.key.first, entry.key.second, e) }
                    .flatMap { triple ->
                        edgesSetByPathPair
                            .getOrDefault(entry.key.swap(), persistentSetOf())
                            .stream()
                            .map { e -> triple to Triple(entry.key.first, entry.key.second, e) }
                    }
            }
    }

    override fun createMinimumSpanningTreeGraphUsingEdgeCostFunction(
        costComparator: Comparator<E>
    ): PathBasedGraph<P, V, E> {
        return edgesSetByPathPair
            .asSequence()
            .flatMap { entry -> entry.value.asSequence().map { e -> entry.key to e } }
            .sortedWith { e1, e2 -> costComparator.compare(e1.second, e2.second) }
            .fold(UnionFindTree.empty<P>() to persistentMapOf<Pair<P, P>, E>()) { acc, entry ->
                val root1AndUnionFindTree: Pair<Option<P>, UnionFindTree<P>> =
                    acc.first.add(entry.first.first).add(entry.first.second).find(entry.first.first)
                val root2AndUnionFindTree: Pair<Option<P>, UnionFindTree<P>> =
                    root1AndUnionFindTree.second.find(entry.first.second)
                val rootPath1: Option<P> = root1AndUnionFindTree.first
                val rootPath2: Option<P> = root2AndUnionFindTree.first
                if (
                    rootPath1
                        .filter({ r1 -> rootPath2.filter { r2 -> r1 != r2 }.isDefined() })
                        .isDefined()
                ) {
                    root2AndUnionFindTree.second.union(
                        rootPath1.orNull()!!,
                        rootPath2.orNull()!!
                    ) to acc.second.put(entry.first, entry.second)
                } else {
                    root2AndUnionFindTree.second to acc.second
                }
            }
            .let { pair: Pair<UnionFindTree<P>, PersistentMap<Pair<P, P>, E>> ->
                DefaultTwoToManyEdgePathBasedGraph(
                    verticesByPath = verticesByPath,
                    edgesSetByPathPair = pair.second.stream().reduceEntriesToPersistentSetValueMap()
                )
            }
    }

    override fun depthFirstSearchOnPath(path: P): Stream<Tuple5<V, P, E, P, V>> {
        return path
            .some()
            .filter { p: P -> verticesByPath.containsKey(p) }
            .stream()
            .flatMap { p: P ->
                StreamSupport.stream(
                    TwoToManyEdgeDepthFirstSearchSpliterator<P, V, E>(
                        inputPath = p,
                        verticesByPath = verticesByPath,
                        edgesSetByPathPair = edgesSetByPathPair,
                        pathConnections = forwardConnections
                    ),
                    false
                )
            }
    }
}
