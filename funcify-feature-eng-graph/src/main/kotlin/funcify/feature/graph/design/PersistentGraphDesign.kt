package funcify.feature.graph.design

import funcify.feature.graph.PersistentGraph
import funcify.feature.graph.container.PersistentGraphContainer
import funcify.feature.graph.container.PersistentGraphContainerFactory
import funcify.feature.graph.template.PersistentGraphTemplate
import java.util.stream.Stream
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf

internal interface PersistentGraphDesign<CWT, P, V, E> : PersistentGraph<P, V, E> {

    val template: PersistentGraphTemplate<CWT>

    override fun get(path: P): V? {
        return when (val container: PersistentGraphContainer<CWT, P, V, E> = this.fold(template)) {
            is PersistentGraphContainerFactory.ParallelizableEdgeGraph ->
                container.verticesByPath[path]
            is PersistentGraphContainerFactory.TwoToOnePathToEdgeGraph ->
                container.verticesByPath[path]
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun get(path1: P, path2: P): Iterable<E> {
        return get(path1 to path2)
    }

    override fun get(pathPair: Pair<P, P>): Iterable<E> {
        return when (val container: PersistentGraphContainer<CWT, P, V, E> = this.fold(template)) {
            is PersistentGraphContainerFactory.ParallelizableEdgeGraph ->
                container.edgesSetByPathPair[pathPair] ?: persistentSetOf<E>()
            is PersistentGraphContainerFactory.TwoToOnePathToEdgeGraph ->
                container.edgesByPathPair[pathPair]?.let { persistentSetOf(it) }
                    ?: persistentSetOf<E>()
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun put(path: P, vertex: V): PersistentGraph<P, V, E> {
        return PutVertexDesign<CWT, P, V, E>(
            template = template,
            currentDesign = this,
            path = path,
            newVertex = vertex
        )
    }

    override fun put(path1: P, path2: P, edge: E): PersistentGraph<P, V, E> {
        return PutEdgeDesign<CWT, P, V, E>(
            template = template,
            currentDesign = this,
            vertexPath1 = path1,
            vertexPath2 = path2,
            newEdge = edge
        )
    }

    override fun put(pathPair: Pair<P, P>, edge: E): PersistentGraph<P, V, E> {
        return PutEdgeDesign<CWT, P, V, E>(
            template = template,
            currentDesign = this,
            vertexPath1 = pathPair.first,
            vertexPath2 = pathPair.second,
            newEdge = edge
        )
    }

    override fun <M : Map<P, V>> putAllVertices(vertices: M): PersistentGraph<P, V, E> {
        return PutVerticesDesign<CWT, P, V, E>(
            template = template,
            currentDesign = this,
            vertices = vertices
        )
    }

    override fun <M : Map<Pair<P, P>, E>> putAllEdges(edges: M): PersistentGraph<P, V, E> {
        return PutEdgesDesign<CWT, P, V, E>(
            template = template,
            currentDesign = this,
            edges = edges
        )
    }

    override fun <S : Set<E>, M : Map<Pair<P, P>, S>> putAllEdgeSets(
        edges: M
    ): PersistentGraph<P, V, E> {
        return PutEdgeSetsDesign<CWT, P, V, E>(
            template = template,
            currentDesign = this,
            edgeSets = edges
        )
    }

    override fun filterVertices(condition: (V) -> Boolean): PersistentGraph<P, V, E> {
        return FilterVerticesDesign<CWT, P, V, E>(
            template = template,
            currentDesign = this,
            condition = condition
        )
    }

    override fun filterEdges(condition: (E) -> Boolean): PersistentGraph<P, V, E> {
        return FilterEdgesDesign<CWT, P, V, E>(
            template = template,
            currentDesign = this,
            condition = condition
        )
    }

    override fun <R> mapVertices(function: (V) -> R): PersistentGraph<P, R, E> {
        return MapVerticesDesign<CWT, P, V, E, R>(
            template = template,
            currentDesign = this,
            mapper = function
        )
    }

    override fun <R> mapEdges(function: (E) -> R): PersistentGraph<P, V, R> {
        return MapEdgesDesign<CWT, P, V, E, R>(
            template = template,
            currentDesign = this,
            mapper = function
        )
    }

    override fun <R, M : Map<out P, R>> flatMapVertices(
        function: (P, V) -> M
    ): PersistentGraph<P, R, E> {
        return FlatMapVerticesDesign<CWT, P, V, E, R, M>(
            template = template,
            currentDesign = this,
            mapper = function
        )
    }

    override fun <R, M : Map<out Pair<P, P>, R>> flatMapEdges(
        function: (Pair<P, P>, E) -> M
    ): PersistentGraph<P, V, R> {
        return FlatMapEdgesDesign<CWT, P, V, E, R, M>(
            template = template,
            currentDesign = this,
            mapper = function
        )
    }

    override fun vertexCount(): Int {
        return when (val container: PersistentGraphContainer<CWT, P, V, E> = this.fold(template)) {
            is PersistentGraphContainerFactory.ParallelizableEdgeGraph ->
                container.verticesByPath.size
            is PersistentGraphContainerFactory.TwoToOnePathToEdgeGraph ->
                container.verticesByPath.size
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun edgeCount(): Int {
        return when (val container: PersistentGraphContainer<CWT, P, V, E> = this.fold(template)) {
            is PersistentGraphContainerFactory.ParallelizableEdgeGraph ->
                container.edgesSetByPathPair.values
                    .stream()
                    .mapToInt { set: PersistentSet<E> -> set.size }
                    .sum()
            is PersistentGraphContainerFactory.TwoToOnePathToEdgeGraph ->
                container.edgesByPathPair.size
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun vertices(): Iterable<V> {
        return when (val container: PersistentGraphContainer<CWT, P, V, E> = this.fold(template)) {
            is PersistentGraphContainerFactory.ParallelizableEdgeGraph ->
                container.verticesByPath.values
            is PersistentGraphContainerFactory.TwoToOnePathToEdgeGraph ->
                container.verticesByPath.values
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun verticesAsStream(): Stream<out V> {
        return when (val container: PersistentGraphContainer<CWT, P, V, E> = this.fold(template)) {
            is PersistentGraphContainerFactory.ParallelizableEdgeGraph ->
                container.verticesByPath.values.stream()
            is PersistentGraphContainerFactory.TwoToOnePathToEdgeGraph ->
                container.verticesByPath.values.stream()
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun edges(): Iterable<E> {
        return when (val container: PersistentGraphContainer<CWT, P, V, E> = this.fold(template)) {
            is PersistentGraphContainerFactory.ParallelizableEdgeGraph ->
                Iterable<E> { edgesAsStream().iterator() }
            is PersistentGraphContainerFactory.TwoToOnePathToEdgeGraph ->
                container.edgesByPathPair.values
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun edgesAsStream(): Stream<out E> {
        return when (val container: PersistentGraphContainer<CWT, P, V, E> = this.fold(template)) {
            is PersistentGraphContainerFactory.ParallelizableEdgeGraph ->
                container.edgesSetByPathPair.values.stream().flatMap { s: PersistentSet<E> ->
                    s.stream()
                }
            is PersistentGraphContainerFactory.TwoToOnePathToEdgeGraph ->
                container.edgesByPathPair.values.stream()
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun connectedPaths(): Iterable<Pair<P, P>> {
        return when (val container: PersistentGraphContainer<CWT, P, V, E> = this.fold(template)) {
            is PersistentGraphContainerFactory.ParallelizableEdgeGraph ->
                container.edgesSetByPathPair.keys
            is PersistentGraphContainerFactory.TwoToOnePathToEdgeGraph ->
                container.edgesByPathPair.keys
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun connectedPathsAsStream(): Stream<out Pair<P, P>> {
        return when (val container: PersistentGraphContainer<CWT, P, V, E> = this.fold(template)) {
            is PersistentGraphContainerFactory.ParallelizableEdgeGraph ->
                container.edgesSetByPathPair.keys.stream()
            is PersistentGraphContainerFactory.TwoToOnePathToEdgeGraph ->
                container.edgesByPathPair.keys.stream()
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun hasCycles(): Boolean {
        return when (val container: PersistentGraphContainer<CWT, P, V, E> = this.fold(template)) {
            is PersistentGraphContainerFactory.ParallelizableEdgeGraph ->
                container.edgesSetByPathPair.keys
                    .parallelStream()
                    .map { pathPair: Pair<P, P> -> pathPair.second to pathPair.first }
                    .anyMatch { pathPair -> pathPair in container.edgesSetByPathPair }
            is PersistentGraphContainerFactory.TwoToOnePathToEdgeGraph ->
                container.edgesByPathPair.keys
                    .parallelStream()
                    .map { pathPair: Pair<P, P> -> pathPair.second to pathPair.first }
                    .anyMatch { pathPair -> pathPair in container.edgesByPathPair }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun getCycles(): Iterable<Pair<Triple<P, P, E>, Triple<P, P, E>>> {
        return Iterable<Pair<Triple<P, P, E>, Triple<P, P, E>>> { getCyclesAsStream().iterator() }
    }

    override fun getCyclesAsStream(): Stream<out Pair<Triple<P, P, E>, Triple<P, P, E>>> {
        return when (val container: PersistentGraphContainer<CWT, P, V, E> = this.fold(template)) {
            is PersistentGraphContainerFactory.ParallelizableEdgeGraph ->
                container.edgesSetByPathPair.keys.parallelStream().flatMap { pathPair: Pair<P, P> ->
                    val reversedPair = pathPair.second to pathPair.first
                    container.edgesSetByPathPair
                        .getOrElse(pathPair) { -> persistentSetOf() }
                        .stream()
                        .flatMap { e1: E ->
                            container.edgesSetByPathPair
                                .getOrElse(reversedPair) { -> persistentSetOf() }
                                .stream()
                                .map { e2: E ->
                                    Triple(pathPair.first, pathPair.second, e1) to
                                        Triple(reversedPair.first, reversedPair.second, e2)
                                }
                        }
                }
            is PersistentGraphContainerFactory.TwoToOnePathToEdgeGraph ->
                container.edgesByPathPair.keys.parallelStream().flatMap { pathPair: Pair<P, P> ->
                    val reversedPair = pathPair.second to pathPair.first
                    val e1: E = container.edgesByPathPair[pathPair]!!
                    when (val e2: E? = container.edgesByPathPair[reversedPair]) {
                        null -> Stream.empty()
                        else ->
                            Stream.of<Pair<Triple<P, P, E>, Triple<P, P, E>>>(
                                Triple(pathPair.first, pathPair.second, e1) to
                                    Triple(reversedPair.first, reversedPair.second, e2)
                            )
                    }
                }
            else -> {
                throw UnsupportedOperationException(
                    "container type is not handled: [ container.type: ${container::class.qualifiedName} ]"
                )
            }
        }
    }

    override fun successorVertices(vertexPath: P): Iterable<Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun successorVerticesAsStream(vertexPath: P): Stream<out Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun successorVertices(vertex: V, pathExtractor: (V) -> P): Iterable<Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun successorVerticesAsStream(
        vertex: V,
        pathExtractor: (V) -> P
    ): Stream<out Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun predecessorVertices(vertexPath: P): Iterable<Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun predecessorVerticesAsStream(vertexPath: P): Stream<out Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun predecessorVertices(vertex: V, pathExtractor: (V) -> P): Iterable<Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun predecessorVerticesAsStream(
        vertex: V,
        pathExtractor: (V) -> P
    ): Stream<out Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun adjacentVertices(vertexPath: P): Iterable<Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun adjacentVerticesAsStream(vertexPath: P): Stream<out Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun adjacentVertices(vertex: V, pathExtractor: (V) -> P): Iterable<Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun adjacentVerticesAsStream(
        vertex: V,
        pathExtractor: (V) -> P
    ): Stream<out Pair<P, V>> {
        TODO("Not yet implemented")
    }

    override fun edgesFromPath(path: P): Iterable<E> {
        TODO("Not yet implemented")
    }

    override fun edgesFromPathAsStream(path: P): Stream<out E> {
        TODO("Not yet implemented")
    }

    override fun edgesToPath(path: P): Iterable<E> {
        TODO("Not yet implemented")
    }

    override fun edgesToPathAsStream(path: P): Stream<out E> {
        TODO("Not yet implemented")
    }

    fun <WT> fold(template: PersistentGraphTemplate<WT>): PersistentGraphContainer<WT, P, V, E>
}
