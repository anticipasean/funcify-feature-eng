package funcify.feature.graph.design

import funcify.feature.graph.GraphDescriptor
import funcify.feature.graph.PersistentGraph
import funcify.feature.graph.behavior.GraphBehavior
import funcify.feature.graph.context.DirectedPersistentGraphContext
import funcify.feature.graph.data.GraphData
import funcify.feature.graph.line.Line
import java.util.logging.Logger
import java.util.stream.Stream
import kotlinx.collections.immutable.ImmutableSet

/**
 * The **design** of a graph includes both its data/contents [GraphData] and its behavior
 * [GraphBehavior]
 *
 * Implementations of a graph **design** are **contexts** e.g. [DirectedPersistentGraphContext]
 */
internal interface PersistentGraphDesign<DWT, P, V, E> : PersistentGraph<P, V, E> {

    companion object {
        private val logger: Logger = Logger.getLogger(PersistentGraphDesign::class.simpleName)
    }

    val behavior: GraphBehavior<DWT>

    val data: GraphData<DWT, P, V, E>

    override fun get(point: P): V? {
        TODO("Not yet implemented")
    }

    override fun get(point1: P, point2: P): Iterable<E> {
        TODO("Not yet implemented")
    }

    override fun get(line: Line<P>): Iterable<E> {
        TODO("Not yet implemented")
    }

    override fun descriptors(): ImmutableSet<GraphDescriptor> {
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

    override fun lines(): Iterable<Line<P>> {
        TODO("Not yet implemented")
    }

    override fun linesAsStream(): Stream<out Line<P>> {
        TODO("Not yet implemented")
    }

    override fun stringify(
        pointStringifier: (P) -> String,
        vertexStringifier: (V) -> String,
        edgeStringifier: (E) -> String
    ): String {
        TODO("Not yet implemented")
    }

    override fun put(point: P, vertex: V): PersistentGraph<P, V, E> {
        TODO("Not yet implemented")
    }

    override fun put(point1: P, point2: P, edge: E): PersistentGraph<P, V, E> {
        TODO("Not yet implemented")
    }

    override fun put(line: Line<P>, edge: E): PersistentGraph<P, V, E> {
        TODO("Not yet implemented")
    }

    override fun <M : Map<out P, V>> putAllVertices(vertices: M): PersistentGraph<P, V, E> {
        TODO("Not yet implemented")
    }

    override fun <M : Map<out Line<P>, E>> putAllEdges(edges: M): PersistentGraph<P, V, E> {
        TODO("Not yet implemented")
    }

    override fun <S : Set<E>, M : Map<out Line<P>, S>> putAllEdgeSets(
        edges: M
    ): PersistentGraph<P, V, E> {
        TODO("Not yet implemented")
    }

    override fun remove(point: P): PersistentGraph<P, V, E> {
        TODO("Not yet implemented")
    }

    override fun filterVertices(condition: (P, V) -> Boolean): PersistentGraph<P, V, E> {
        TODO("Not yet implemented")
    }

    override fun filterEdges(condition: (Line<P>, E) -> Boolean): PersistentGraph<P, V, E> {
        TODO("Not yet implemented")
    }

    override fun <P1> mapPoints(function: (P, V) -> P1): PersistentGraph<P1, V, E> {
        TODO("Not yet implemented")
    }

    override fun <V1> mapVertices(function: (P, V) -> V1): PersistentGraph<P, V1, E> {
        TODO("Not yet implemented")
    }

    override fun <E1> mapEdges(function: (Line<P>, E) -> E1): PersistentGraph<P, V, E1> {
        TODO("Not yet implemented")
    }

    override fun <P1, V1, M : Map<out P1, V1>> flatMapVertices(
        function: (P, V) -> M
    ): PersistentGraph<P1, V1, E> {
        TODO("Not yet implemented")
    }

    override fun <E1, M : Map<out Line<P>, E1>> flatMapEdges(
        function: (Line<P>, E) -> M
    ): PersistentGraph<P, V, E1> {
        TODO("Not yet implemented")
    }
}
