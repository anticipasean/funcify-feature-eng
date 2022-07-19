package funcify.feature.tools.container.graph

import arrow.core.Option
import arrow.core.Tuple5
import arrow.core.getOrElse
import arrow.core.some
import arrow.core.toOption
import funcify.feature.tools.container.tree.UnionFindTree
import funcify.feature.tools.extensions.OptionExtensions.flatMapOptions
import funcify.feature.tools.extensions.OptionExtensions.stream
import funcify.feature.tools.extensions.OptionExtensions.toPersistentSet
import funcify.feature.tools.extensions.PersistentListExtensions.reduceToPersistentList
import funcify.feature.tools.extensions.PersistentMapExtensions.reduceEntriesToPersistentMap
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentSetValueMap
import java.util.stream.Stream
import java.util.stream.StreamSupport
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

internal data class DefaultTwoToOnePathToEdgePathBasedGraph<P, V, E>(
    override val verticesByPath: PersistentMap<P, V> = persistentMapOf(),
    override val edgesByPathPair: PersistentMap<Pair<P, P>, E> = persistentMapOf()
) : TwoToOnePathToEdgeGraph<P, V, E> {

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
        edgesByPathPair
            .stream()
            .parallel()
            .map { e: Map.Entry<Pair<P, P>, E> ->
                verticesByPath[e.key.first].toOption().zip(
                    verticesByPath[e.key.second].toOption()
                ) { v1: V, v2: V -> (v1 to v2) to e.value }
            }
            .flatMapOptions()
            .reducePairsToPersistentSetValueMap()
    }

    /**
     * Lazily initialized path_connections map: e.g. { ( parent_path_0 to child_path_0 ), ... (
     * parent_path_n-1 to child_path_n-1 ) } detailing what "child" paths each path has if
     * directionality is being used in the algorithm in question
     */
    private val pathConnections: ImmutableMap<P, ImmutableSet<P>> by lazy {
        edgesByPathPair.keys.stream().parallel().reducePairsToPersistentSetValueMap()
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

    override fun putVertex(path: P, vertex: V): PathBasedGraph<P, V, E> {
        return DefaultTwoToOnePathToEdgePathBasedGraph(
            verticesByPath.put(path, vertex),
            edgesByPathPair
        )
    }

    override fun <M : Map<out P, @UnsafeVariance V>> putAllVertices(
        vertices: M
    ): PathBasedGraph<P, V, E> {
        return DefaultTwoToOnePathToEdgePathBasedGraph<P, V, E>(
            verticesByPath = verticesByPath.putAll(vertices),
            edgesByPathPair = edgesByPathPair
        )
    }

    override fun removeVertex(path: P): PathBasedGraph<P, V, E> {

        return if (path in verticesByPath) {
            DefaultTwoToOnePathToEdgePathBasedGraph<P, V, E>(
                    verticesByPath = verticesByPath.remove(path)
                )
                .putAllEdges(edgesByPathPair)
        } else {
            this
        }
    }

    override fun <S : Set<P>> removeAllVertices(paths: S): PathBasedGraph<P, V, E> {
        return DefaultTwoToOnePathToEdgePathBasedGraph<P, V, E>(
                verticesByPath =
                    paths
                        .parallelStream()
                        .reduce(
                            verticesByPath,
                            { v, p -> v.remove(p) },
                            { v1, v2 -> v1.putAll(v2) }
                        )
            )
            .putAllEdges(edgesByPathPair)
    }

    /** Do not add an edge that does not have a corresponding vertex path */
    override fun putEdge(path1: P, path2: P, edge: E): PathBasedGraph<P, V, E> {
        return if (verticesByPath.containsKey(path1) && verticesByPath.containsKey(path2)) {
            DefaultTwoToOnePathToEdgePathBasedGraph(
                verticesByPath = verticesByPath,
                edgesByPathPair = edgesByPathPair.put(Pair(path1, path2), edge)
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
            DefaultTwoToOnePathToEdgePathBasedGraph(
                verticesByPath = verticesByPath,
                edgesByPathPair = edgesByPathPair.put(connectedPaths, edge)
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
                .filter { entry ->
                    verticesByPath.containsKey(entry.key.first) &&
                        verticesByPath.containsKey(entry.key.second)
                }
                .reduceEntriesToPersistentMap()
        return DefaultTwoToOnePathToEdgePathBasedGraph(
            verticesByPath = verticesByPath,
            edgesByPathPair = updatedEdges
        )
    }

    override fun <S : Set<@UnsafeVariance E>, M : Map<out Pair<P, P>, S>> putAllEdgeSets(
        edges: M
    ): PathBasedGraph<P, V, E> {
        val updatedEdges =
            edges.entries
                .parallelStream()
                .flatMap { e -> e.value.parallelStream().map { edge -> e.key to edge } }
                .filter { pair ->
                    verticesByPath.containsKey(pair.first.first) &&
                        verticesByPath.containsKey(pair.first.second)
                }
                .reducePairsToPersistentMap()
        return DefaultTwoToOnePathToEdgePathBasedGraph(
            verticesByPath = verticesByPath,
            edgesByPathPair = updatedEdges
        )
    }

    override fun getEdgesFromPathToPath(path1: P, path2: P): ImmutableSet<E> {
        return edgesByPathPair[Pair(path1, path2)].toOption().toPersistentSet()
    }

    /**
     * Overrides default implementation making use of lazily initiated path_connections map, which
     * should be faster with larger graphs since not all path connections have to be streamed
     * through
     */
    override fun getEdgesFrom(path: P): Stream<E> {
        return pathConnections[path]
            .toOption()
            .map { ps: ImmutableSet<P> -> ps.stream().map { p: P -> path to p } }
            .getOrElse { Stream.empty() }
            .map { pair: Pair<P, P> -> edgesByPathPair[pair].toOption() }
            .flatMapOptions()
    }

    /**
     * Overrides default implementation making use of lazily initiated path_connections map, which
     * should be faster with larger graphs since not all path connections have to be streamed
     * through
     */
    override fun getEdgesTo(path: P): Stream<E> {
        return pathConnections[path]
            .toOption()
            .map { ps: ImmutableSet<P> -> ps.stream().map { p: P -> p to path } }
            .getOrElse { Stream.empty() }
            .map { pair: Pair<P, P> -> edgesByPathPair[pair].toOption() }
            .flatMapOptions()
    }

    override fun successors(vertexPath: P): Stream<Pair<P, V>> {
        return pathConnections[vertexPath]
            .toOption()
            .getOrElse { persistentSetOf() }
            .stream()
            .filter { p: P -> p != vertexPath }
            .flatMap { p: P -> getVertex(p).map({ v -> p to v }).stream() }
    }

    override fun successors(vertex: V, pathExtractor: Function1<V, P>): Stream<Pair<P, V>> {
        return successors(pathExtractor.invoke(vertex))
    }

    override fun predecessors(vertexPath: P): Stream<Pair<P, V>> {
        return pathConnections
            .stream()
            .filter { (_, value) -> value.stream().anyMatch { p: P -> p == vertexPath } }
            .map { (key, _) -> key }
            .filter { p: P -> p != vertexPath }
            .flatMap { p: P -> verticesByPath[p].toOption().map { v: V -> p to v }.stream() }
    }

    override fun predecessors(vertex: V, pathExtractor: Function1<V, P>): Stream<Pair<P, V>> {
        return predecessors(pathExtractor.invoke(vertex))
    }

    override fun adjacentVertices(vertexPath: P): Stream<Pair<P, V>> {
        return Stream.concat(predecessors(vertexPath), successors(vertexPath))
    }

    override fun adjacentVertices(vertex: V, pathExtractor: (V) -> P): Stream<Pair<P, V>> {
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
            edgesByPathPair
                .stream()
                .parallel()
                .filter { entry: Map.Entry<Pair<P, P>, E> ->
                    sequenceOf(entry.key.first, entry.key.second).all { p ->
                        updatedVertices.containsKey(p)
                    }
                }
                .reduceEntriesToPersistentMap()
        return DefaultTwoToOnePathToEdgePathBasedGraph(updatedVertices, updatedEdges)
    }

    override fun filterEdges(function: (E) -> Boolean): PathBasedGraph<P, V, E> {
        val updatedEdges =
            edgesByPathPair
                .stream()
                .parallel()
                .filter { entry: Map.Entry<Pair<P, P>, E> -> function.invoke(entry.value) }
                .reduceEntriesToPersistentMap()
        return DefaultTwoToOnePathToEdgePathBasedGraph(
            verticesByPath = verticesByPath,
            edgesByPathPair = updatedEdges
        )
    }

    override fun <R> mapVertices(function: (V) -> R): PathBasedGraph<P, R, E> {
        val updatedVertices =
            verticesByPath
                .stream()
                .parallel()
                .map { entry: Map.Entry<P, V> -> entry.key to function.invoke(entry.value) }
                .reducePairsToPersistentMap()
        val updatedEdges =
            edgesByPathPair
                .stream()
                .parallel()
                .filter { entry: Map.Entry<Pair<P, P>, E> ->
                    sequenceOf(entry.key.first, entry.key.second).all { p ->
                        updatedVertices.containsKey(p)
                    }
                }
                .reduceEntriesToPersistentMap()
        return DefaultTwoToOnePathToEdgePathBasedGraph(
            verticesByPath = updatedVertices,
            edgesByPathPair = updatedEdges
        )
    }

    override fun <R> mapEdges(function: (E) -> R): PathBasedGraph<P, V, R> {
        val updatedEdges =
            edgesByPathPair
                .stream()
                .parallel()
                .map { entry: Map.Entry<Pair<P, P>, E> ->
                    entry.key to function.invoke(entry.value)
                }
                .reducePairsToPersistentMap()
        return DefaultTwoToOnePathToEdgePathBasedGraph(
            verticesByPath = verticesByPath,
            edgesByPathPair = updatedEdges
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
        return DefaultTwoToOnePathToEdgePathBasedGraph<P, R, E>(verticesByPath = updatedVertices)
            .putAllEdges(edgesByPathPair)
    }

    override fun <R, M : Map<out Pair<P, P>, @UnsafeVariance R>> flatMapEdges(
        function: (Pair<P, P>, E) -> M
    ): PathBasedGraph<P, V, R> {
        val updatedEdges =
            edgesByPathPair.entries
                .parallelStream()
                .map { (key, value) -> function.invoke(key, value) }
                .flatMap { edgesMap: M -> edgesMap.entries.parallelStream() }
                .reduceEntriesToPersistentMap()
        return DefaultTwoToOnePathToEdgePathBasedGraph<P, V, R>(verticesByPath = verticesByPath)
            .putAllEdges(updatedEdges)
    }

    override fun hasCycles(): Boolean {
        return edgesByPathPair
            .stream()
            .parallel()
            .map { entry: Map.Entry<Pair<P, P>, E> -> entry.key.swap() }
            .anyMatch { pair: Pair<P, P> -> edgesByPathPair.containsKey(pair) }
    }

    override fun getCycles(): Stream<Pair<Triple<P, P, E>, Triple<P, P, E>>> {
        return edgesByPathPair
            .stream()
            .parallel()
            .filter { entry: Map.Entry<Pair<P, P>, E> ->
                edgesByPathPair.containsKey(entry.key.swap())
            }
            .map { entry: Map.Entry<Pair<P, P>, E> ->
                Pair(
                    Triple(entry.key.first, entry.key.second, entry.value),
                    Triple(
                        entry.key.second,
                        entry.key.first,
                        edgesByPathPair[Pair(entry.key.second, entry.key.first)]!!
                    )
                )
            }
    }

    override fun createMinimumSpanningTreeGraphUsingEdgeCostFunction(
        costComparator: Comparator<E>
    ): PathBasedGraph<P, V, E> {
        return edgesByPathPair
            .asSequence()
            .sortedWith { e1, e2 -> costComparator.compare(e1.value, e2.value) }
            .fold(UnionFindTree.empty<P>() to persistentMapOf<Pair<P, P>, E>()) { acc, entry ->
                val root1AndUnionFindTree: Pair<Option<P>, UnionFindTree<P>> =
                    acc.first.add(entry.key.first).add(entry.key.second).find(entry.key.first)
                val root2AndUnionFindTree: Pair<Option<P>, UnionFindTree<P>> =
                    root1AndUnionFindTree.second.find(entry.key.second)
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
                    ) to acc.second.put(entry.key, entry.value)
                } else {
                    root2AndUnionFindTree.second to acc.second
                }
            }
            .let { pair: Pair<UnionFindTree<P>, PersistentMap<Pair<P, P>, E>> ->
                DefaultTwoToOnePathToEdgePathBasedGraph(
                    verticesByPath = verticesByPath,
                    edgesByPathPair = pair.second
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
                    TwoToOneEdgeDepthFirstSearchSpliterator<P, V, E>(
                        inputPath = p,
                        verticesByPath = verticesByPath,
                        edgesByPathPair = edgesByPathPair,
                        pathConnections = pathConnections
                    ),
                    false
                )
            }
    }
}
