package funcify.feature.graph

import funcify.feature.graph.line.DirectedLine
import funcify.feature.graph.line.Line
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.platform.commons.logging.Logger
import org.junit.platform.commons.logging.LoggerFactory

/**
 *
 * @author smccarron
 * @created 2022-11-29
 */
class BasicDirectedPersistentGraphTest {

    companion object {
        private val logger: Logger =
            LoggerFactory.getLogger(BasicDirectedPersistentGraphTest::class.java)

        fun <P, V, E> createEmptyDirectedGraph(): DirectedPersistentGraph<P, V, E> {
            return PersistentGraphFactory.defaultFactory().builder().directed().build<P, V, E>()
        }
    }

    @Test
    fun flatMapVerticesTwoToOnePathsToEdgeGraphTest() {
        val g1 =
            (0..6).fold(createEmptyDirectedGraph()) { acc: PersistentGraph<Int, Int, Int>, i: Int ->
                acc.put(i, i)
            }
        val g2 =
            (0..5).fold(g1) { acc: PersistentGraph<Int, Int, Int>, i: Int -> acc.put(i, i + 1, i) }
        Assertions.assertEquals(7, g2.vertexCount())
        Assertions.assertEquals(6, g2.edgeCount())
        val g3 =
            g2.flatMapVertices { p: Int, v: Int ->
                mutableMapOf<Int, Char>(p to ('A'.code + v).toChar())
            }
        Assertions.assertEquals('G', g3[6])
        Assertions.assertEquals(1, g3[5, 6].count())
        Assertions.assertEquals(5, g3[5, 6].first())
    }

    /**
     * * Assuming
     * - graph of type PersistentGraph<Int, Char, Double>
     * ```
     *      - with vertices { (1, 'A'), (2, 'B') } and edge { ( (1, 2), 0.2 ) }
     * ```
     * - a flatmap function of type (Int, Char) -> Map<String, Char>
     * - a lambda implementation of {(p: Int, v: Char) -> mapOf(p.toString() to v, (p *
     * 10).toString() to (v + 2)) }
     *
     * We would expect the result to be a graph with:
     * - vertices { ("1", 'A'), ("10", 'C'), ("2", 'B'), ("20", 'D') } and
     * - edges { (("1", "2"), 0.2), (("1", "20"), 0.2), (("10", "2"), 0.2), (("10", "20"), 0.2) }
     */
    @Test
    fun flatMapVerticesCartesianProductTest() {
        val g1: DirectedPersistentGraph<Int, Char, Double> =
            createEmptyDirectedGraph<Int, Char, Double>()
                .putAllVertices(mapOf(1 to 'A', 2 to 'B'))
                .putEdge(1, 2, 0.2)
        Assertions.assertTrue(1 in g1, "vertex 1 missing")
        Assertions.assertTrue(2 in g1, "vertex 2 missing")
        Assertions.assertTrue(DirectedLine.of(1, 2) in g1, "edge missing")
        val g2: DirectedPersistentGraph<String, Char, Double> =
            g1.flatMapVertices { p: Int, v: Char ->
                mapOf(p.toString() to v, (p * 10).toString() to (v + 2))
            }
        Assertions.assertEquals(4, g2.vertexCount(), "flatMapVertices incorrectly applied")
        Assertions.assertTrue("10" in g2, "flatMapVertices unexpected result")
        Assertions.assertTrue("20" in g2, "flatMapVertices unexpected result")
        Assertions.assertEquals(4, g2.edgeCount())
        Assertions.assertTrue(g2.edgesAsStream().allMatch { d -> d == 0.2 })
        Assertions.assertTrue(DirectedLine.of("1", "2") in g2)
        Assertions.assertTrue(DirectedLine.of("1", "20") in g2)
        Assertions.assertTrue(DirectedLine.of("10", "2") in g2)
        Assertions.assertTrue(DirectedLine.of("10", "20") in g2)
    }

    @Test
    fun mapVerticesTwoToOnePathsToEdgeGraphTest() {
        val g1 =
            (0..6).fold(createEmptyDirectedGraph<Int, Int, Int>()) {
                acc: DirectedPersistentGraph<Int, Int, Int>,
                i: Int ->
                acc.putVertex(i, i)
            }
        val g2 =
            (0..5).fold(g1) { acc: DirectedPersistentGraph<Int, Int, Int>, i: Int ->
                acc.putEdge(i, i + 1, i)
            }
        Assertions.assertEquals(7, g2.vertexCount())
        Assertions.assertEquals(6, g2.edgeCount())
        val g3 = g2.mapVertices { v: Int -> ('A'.code + v).toChar() }
        Assertions.assertEquals('G', g3[6])
        Assertions.assertEquals(1, g3[5, 6].count())
        Assertions.assertEquals(5, g3[5, 6].first())
    }

    @Test
    fun filterVerticesTwoToOnePathsToEdgeGraphTest() {
        val g1 =
            (0..6).fold(createEmptyDirectedGraph<Int, Int, Int>()) {
                acc: DirectedPersistentGraph<Int, Int, Int>,
                i: Int ->
                acc.putVertex(i, i)
            }
        val g2 =
            (0..5).fold(g1) { acc: DirectedPersistentGraph<Int, Int, Int>, i: Int ->
                acc.putEdge(i, i + 1, i)
            }
        Assertions.assertEquals(7, g2.vertexCount())
        Assertions.assertEquals(6, g2.edgeCount())
        val g3 = g2.filterVertices { v: Int -> v != 6 }
        Assertions.assertEquals(6, g3.vertexCount())
        Assertions.assertEquals(5, g3.vertices().last())
        Assertions.assertEquals(emptySet<Int>(), g3[5, 6].toSet())
    }

    @Test
    fun flatMapEdgesTwoToOnePathsToEdgeGraphTest() {
        val g1 =
            (0..6).fold(createEmptyDirectedGraph<Int, Int, Int>()) {
                acc: DirectedPersistentGraph<Int, Int, Int>,
                i: Int ->
                acc.putVertex(i, i)
            }
        val g2 =
            (0..5).fold(g1) { acc: DirectedPersistentGraph<Int, Int, Int>, i: Int ->
                acc.putEdge(i, i + 1, i)
            }
        Assertions.assertEquals(7, g2.vertexCount())
        Assertions.assertEquals(6, g2.edgeCount())
        val g3 =
            g2.flatMapEdges { line: Line<Int>, e: Int ->
                mutableMapOf<Line<Int>, Char>(line to ('A'.code + e).toChar())
            }
        Assertions.assertEquals(6, g3[6])
        Assertions.assertEquals(1, g3[5, 6].count())
        Assertions.assertEquals('F', g3[5, 6].first())
    }

    @Test
    fun mapEdgesTwoToOnePathsToEdgeGraphTest() {
        val g1 =
            (0..6).asSequence().fold(createEmptyDirectedGraph<Int, Int, Int>()) {
                acc: DirectedPersistentGraph<Int, Int, Int>,
                i: Int ->
                acc.putVertex(i, i)
            }
        val g2 =
            (0..5).fold(g1) { acc: DirectedPersistentGraph<Int, Int, Int>, i: Int ->
                acc.putEdge(i, i + 1, i)
            }
        Assertions.assertEquals(7, g2.vertexCount())
        Assertions.assertEquals(6, g2.edgeCount())
        val g3 = g2.mapEdges { e: Int -> ('A'.code + e).toChar() }
        Assertions.assertEquals(6, g3[6])
        Assertions.assertEquals(1, g3[5, 6].count())
        Assertions.assertEquals('F', g3[5, 6].first())
    }

    @Test
    fun filterEdgesTwoToOnePathsToEdgeGraphTest() {
        val g1 =
            (0..6).asSequence().fold(createEmptyDirectedGraph<Int, Int, Int>()) {
                acc: DirectedPersistentGraph<Int, Int, Int>,
                i: Int ->
                acc.putVertex(i, i)
            }
        val g2 =
            (0..5).fold(g1) { acc: DirectedPersistentGraph<Int, Int, Int>, i: Int ->
                acc.putEdge(i, i + 1, i)
            }
        Assertions.assertEquals(7, g2.vertexCount())
        Assertions.assertEquals(6, g2.edgeCount())
        val g3 = g2.filterEdges { e: Int -> e != 5 }
        Assertions.assertEquals(5, g3.edgeCount())
        Assertions.assertEquals(6, g3[6])
        Assertions.assertEquals(emptySet<Int>(), g3[5, 6].toSet())
    }

    @Test
    fun successorsTest() {
        val vertices: IntRange = 1..12
        val edges: Sequence<Triple<Int, Int, Int>> =
            sequenceOf(
                Triple(1, 2, 1),
                Triple(2, 3, 1),
                Triple(2, 6, 1),
                Triple(3, 4, 1),
                Triple(3, 5, 1),
                Triple(1, 7, 1),
                Triple(1, 8, 1),
                Triple(8, 9, 1),
                Triple(8, 12, 1),
                Triple(9, 10, 1),
                Triple(9, 11, 1)
            )
        val graph: DirectedPersistentGraph<Int, Int, Int> =
            edges.fold(vertices.fold(createEmptyDirectedGraph()) { g, i -> g.putVertex(i, i) }) {
                g,
                e ->
                g.putEdge(e.first, e.second, e.third)
            }
        Assertions.assertEquals(setOf(2 to 2, 7 to 7, 8 to 8), graph.successorVertices(1).toSet())
    }

    @Test
    fun predecessorsTest() {
        val vertices: IntRange = 1..12
        val edges: Sequence<Triple<Int, Int, Int>> =
            sequenceOf(
                Triple(1, 2, 1),
                Triple(2, 3, 1),
                Triple(2, 6, 1),
                Triple(3, 4, 1),
                Triple(3, 5, 1),
                Triple(1, 7, 1),
                Triple(1, 8, 1),
                Triple(8, 9, 1),
                Triple(8, 12, 1),
                Triple(9, 10, 1),
                Triple(9, 11, 1)
            )
        val graph: DirectedPersistentGraph<Int, Int, Int> =
            edges.fold(vertices.fold(createEmptyDirectedGraph()) { g, i -> g.putVertex(i, i) }) {
                g,
                e ->
                g.putEdge(e.first, e.second, e.third)
            }
        Assertions.assertEquals(setOf(1 to 1), graph.predecessorVertices(8).toSet())
    }
}
