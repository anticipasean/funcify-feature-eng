package funcify.feature.graph.design

import arrow.core.Tuple5
import funcify.feature.graph.PersistentGraph
import funcify.feature.graph.container.PersistentGraphContainer
import funcify.feature.graph.container.PersistentGraphContainerFactory
import funcify.feature.graph.template.PersistentGraphTemplate
import java.util.stream.Stream
import kotlinx.collections.immutable.persistentSetOf

internal interface PersistentGraphDesign<CWT, P, V, E> : PersistentGraph<P, V, E> {

    val template: PersistentGraphTemplate<CWT>

    override fun get(path: P): V? {
        return when (val container: PersistentGraphContainer<CWT, P, V, E> = this.fold(template)) {
            is PersistentGraphContainerFactory.TwoToManyPathToEdgeGraph ->
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
            is PersistentGraphContainerFactory.TwoToManyPathToEdgeGraph ->
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
        TODO("Not yet implemented")
    }

    override fun put(path1: P, path2: P, edge: E): PersistentGraph<P, V, E> {
        TODO("Not yet implemented")
    }

    override fun put(pathPair: Pair<P, P>, edge: E): PersistentGraph<P, V, E> {
        TODO("Not yet implemented")
    }

    override fun <M : Map<P, V>> putAllVertices(vertices: M): PersistentGraph<P, V, E> {
        TODO("Not yet implemented")
    }

    override fun <M : Map<Pair<P, P>, E>> putAllEdges(edges: M): PersistentGraph<P, V, E> {
        TODO("Not yet implemented")
    }

    override fun <S : Set<E>, M : Map<Pair<P, P>, S>> putAllEdgeSets(
        edges: M
    ): PersistentGraph<P, V, E> {
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }

    override fun <R> mapEdges(function: (E) -> R): PersistentGraph<P, V, R> {
        TODO("Not yet implemented")
    }

    override fun <R, M : Map<out P, R>> flatMapVertices(
        function: (P, V) -> M
    ): PersistentGraph<P, R, E> {
        TODO("Not yet implemented")
    }

    override fun <R, M : Map<out Pair<P, P>, R>> flatMapEdges(
        function: (Pair<P, P>, E) -> M
    ): PersistentGraph<P, V, R> {
        TODO("Not yet implemented")
    }

    override fun vertexCount(): Int {
        TODO("Not yet implemented")
    }

    override fun edgeCount(): Int {
        TODO("Not yet implemented")
    }

    override fun vertices(): Iterable<V> {
        TODO("Not yet implemented")
    }

    override fun verticesAsStream(): Stream<out V> {
        TODO("Not yet implemented")
    }

    override fun edges(): Iterable<E> {
        TODO("Not yet implemented")
    }

    override fun edgesAsStream(): Stream<out E> {
        TODO("Not yet implemented")
    }

    override fun connectedPaths(): Iterable<Pair<P, P>> {
        TODO("Not yet implemented")
    }

    override fun connectedPathsAsStream(): Stream<out Pair<P, P>> {
        TODO("Not yet implemented")
    }

    override fun hasCycles(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getCycles(): Iterable<Pair<Triple<P, P, E>, Triple<P, P, E>>> {
        TODO("Not yet implemented")
    }

    override fun getCyclesAsStream(): Stream<out Pair<Triple<P, P, E>, Triple<P, P, E>>> {
        TODO("Not yet implemented")
    }

    override fun depthFirstSearchOnPath(path: P): Stream<out Tuple5<V, P, E, P, V>> {
        TODO("Not yet implemented")
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
