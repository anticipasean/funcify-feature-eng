package funcify.feature.graph.design

import funcify.feature.graph.GraphDescriptor
import funcify.feature.graph.PersistentGraph
import funcify.feature.graph.behavior.GraphBehavior
import funcify.feature.graph.context.StandardDirectedPersistentGraphContext
import funcify.feature.graph.data.GraphData
import funcify.feature.graph.line.DirectedLine
import funcify.feature.graph.line.Line
import java.util.logging.Logger
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlinx.collections.immutable.ImmutableSet

/**
 * The **design** of a graph includes both its data/contents [GraphData] and its behavior
 * [GraphBehavior]
 *
 * Implementations of a graph **design** are **contexts** e.g. [StandardDirectedPersistentGraphContext]
 */
internal interface PersistentGraphDesign<DWT, P, V, E> : PersistentGraph<P, V, E> {

    companion object {
        private val logger: Logger = Logger.getLogger(PersistentGraphDesign::class.simpleName)
    }

    val behavior: GraphBehavior<DWT>

    val data: GraphData<DWT, P, V, E>

    fun <P, V, E> unit(data: GraphData<DWT, P, V, E>): PersistentGraphDesign<DWT, P, V, E>

    override fun descriptors(): ImmutableSet<GraphDescriptor> {
        return behavior.descriptors()
    }

    override fun contains(point: P): Boolean {
        return behavior.contains(data, point)
    }

    override fun get(point: P): V? {
        return behavior.get(data, point)
    }

    override fun contains(point1: P, point2: P): Boolean {
        return behavior.contains(data, point1, point2)
    }

    override fun get(point1: P, point2: P): Iterable<E> {
        return behavior.get(data, point1, point2)
    }

    override fun contains(line: Line<P>): Boolean {
        return behavior.contains(data, line)
    }

    override fun get(line: Line<P>): Iterable<E> {
        return behavior.get(data, line)
    }

    override fun vertexCount(): Int {
        return behavior.verticesByPoint(data).size
    }

    override fun edgeCount(): Int {
        return behavior.edges(data).count()
    }

    override fun vertices(): Iterable<V> {
        return Iterable { verticesAsStream().iterator() }
    }

    override fun verticesAsStream(): Stream<out V> {
        return behavior.streamVertices(data).map { (_: P, v: V) -> v }
    }

    override fun edges(): Iterable<E> {
        return Iterable { edgesAsStream().iterator() }
    }

    override fun edgesAsStream(): Stream<out E> {
        return behavior.streamEdges(data).map { (_: Line<P>, e: E) -> e }
    }

    override fun lines(): Iterable<Line<P>> {
        return Iterable { linesAsStream().iterator() }
    }

    override fun linesAsStream(): Stream<out Line<P>> {
        return behavior.streamEdges(data).map { (l: Line<P>, _: E) -> l }
    }

    override fun <T> foldLeftVertices(initial: T, accumulator: (T, Pair<P, V>) -> T): T {
        return behavior.foldLeftVertices(data, initial, accumulator)
    }

    override fun <T> foldLeftEdges(initial: T, accumulator: (T, Pair<Line<P>, E>) -> T): T {
        return behavior.foldLeftEdges(data, initial, accumulator)
    }

    override fun <T> foldRightVertices(initial: T, accumulator: (Pair<P, V>, T) -> T): T {
        return behavior.foldRightVertices(data, initial, accumulator)
    }

    override fun <T> foldRightEdges(initial: T, accumulator: (Pair<Line<P>, E>, T) -> T): T {
        return behavior.foldRightEdges(data, initial, accumulator)
    }

    override fun stringify(
        pointStringifier: (P) -> String,
        vertexStringifier: (V) -> String,
        edgeStringifier: (E) -> String
    ): String {
        val lineStringifier: (Line<P>) -> String = { l: Line<P> ->
            val (p1, p2) = l
            if (l is DirectedLine) {
                """"line":{"source":${pointStringifier(p1)},"destination":${pointStringifier(p2)}}"""
            } else {
                """"line":{"first":${pointStringifier(p1)},"second":${pointStringifier(p2)}}"""
            }
        }
        val vertexByPointStringifier: (P, V) -> String = { p, v ->
            """{"point":${pointStringifier(p)},"vertex":${vertexStringifier(v)}}"""
        }
        val edgeByLineStringifier: (Line<P>, E) -> String = { l, e ->
            """{${lineStringifier(l)},"edge":${edgeStringifier(e)}}"""
        }
        return StringBuilder("{")
            .append(""""vertices":[""")
            .append(
                behavior
                    .streamVertices(data)
                    .map { (p: P, v: V) -> vertexByPointStringifier(p, v) }
                    .collect(Collectors.joining(","))
            )
            .append("]")
            .append(",")
            .append(""""edges":[""")
            .append(
                behavior
                    .streamEdges(data)
                    .map { (l: Line<P>, e: E) -> edgeByLineStringifier(l, e) }
                    .collect(Collectors.joining(","))
            )
            .append("]")
            .append("}")
            .toString()
    }
}
