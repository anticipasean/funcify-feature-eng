package funcify.feature.graph

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.streams.asSequence

/**
 *
 * @author smccarron
 * @created 2022-11-29
 */
class BasicPersistentGraphTest {

    @Test
    fun flatMapVerticesTwoToOnePathsToEdgeGraphTest() {
        val g1 =
            (0..6).fold(PersistentGraph.empty<Int, Int, Int>()) {
                    acc: PersistentGraph<Int, Int, Int>,
                    i: Int ->
                acc.put(i, i)
            }
        val g2 =
            (0..5).fold(g1) { acc: PersistentGraph<Int, Int, Int>, i: Int ->
                acc.put(i, i + 1, i)
            }
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

//    @Test
//    fun mapVerticesTwoToOnePathsToEdgeGraphTest() {
//        val g1 =
//            (0..6).fold(PathBasedGraph.emptyTwoToOnePathsToEdgeGraph<Int, Int, Int>()) {
//                    acc: PathBasedGraph<Int, Int, Int>,
//                    i: Int ->
//                acc.putVertex(i, i)
//            }
//        val g2 =
//            (0..5).fold(g1) { acc: PathBasedGraph<Int, Int, Int>, i: Int ->
//                acc.putEdge(i, i + 1, i)
//            }
//        Assertions.assertEquals(7, g2.vertexCount())
//        Assertions.assertEquals(6, g2.edgeCount())
//        val g3 = g2.mapVertices { v: Int -> ('A'.code + v).toChar() }
//        Assertions.assertEquals('G', g3.verticesByPath[6])
//        Assertions.assertEquals(1, g3.getEdgesFromPathToPath(5, 6).size)
//        Assertions.assertEquals(5, g3.getEdgesFromPathToPath(5, 6).first())
//    }
//
//    @Test
//    fun filterVerticesTwoToOnePathsToEdgeGraphTest() {
//        val g1 =
//            (0..6).fold(PathBasedGraph.emptyTwoToOnePathsToEdgeGraph<Int, Int, Int>()) {
//                    acc: PathBasedGraph<Int, Int, Int>,
//                    i: Int ->
//                acc.putVertex(i, i)
//            }
//        val g2 =
//            (0..5).fold(g1) { acc: PathBasedGraph<Int, Int, Int>, i: Int ->
//                acc.putEdge(i, i + 1, i)
//            }
//        Assertions.assertEquals(7, g2.vertexCount())
//        Assertions.assertEquals(6, g2.edgeCount())
//        val g3 = g2.filterVertices { v: Int -> v != 6 }
//        Assertions.assertEquals(6, g3.vertexCount())
//        Assertions.assertEquals(5, g3.verticesByPath.asIterable().last().value)
//        Assertions.assertEquals(emptySet<Int>(), g3.getEdgesFromPathToPath(5, 6).toSet())
//    }
//
//    @Test
//    fun flatMapEdgesTwoToOnePathsToEdgeGraphTest() {
//        val g1 =
//            (0..6).fold(PathBasedGraph.emptyTwoToOnePathsToEdgeGraph<Int, Int, Int>()) {
//                    acc: PathBasedGraph<Int, Int, Int>,
//                    i: Int ->
//                acc.putVertex(i, i)
//            }
//        val g2 =
//            (0..5).fold(g1) { acc: PathBasedGraph<Int, Int, Int>, i: Int ->
//                acc.putEdge(i, i + 1, i)
//            }
//        Assertions.assertEquals(7, g2.vertexCount())
//        Assertions.assertEquals(6, g2.edgeCount())
//        val g3 =
//            g2.flatMapEdges { pair: Pair<Int, Int>, e: Int ->
//                mutableMapOf<Pair<Int, Int>, Char>(pair to ('A'.code + e).toChar())
//            }
//        Assertions.assertEquals(6, g3.verticesByPath[6])
//        Assertions.assertEquals(1, g3.getEdgesFromPathToPath(5, 6).size)
//        Assertions.assertEquals('F', g3.getEdgesFromPathToPath(5, 6).first())
//    }
//
//    @Test
//    fun mapEdgesTwoToOnePathsToEdgeGraphTest() {
//        val g1 =
//            (0..6).asSequence().fold(
//                PathBasedGraph.emptyTwoToOnePathsToEdgeGraph<Int, Int, Int>()
//                                    ) { acc: PathBasedGraph<Int, Int, Int>, i: Int -> acc.putVertex(i, i) }
//        val g2 =
//            (0..5).fold(g1) { acc: PathBasedGraph<Int, Int, Int>, i: Int ->
//                acc.putEdge(i, i + 1, i)
//            }
//        Assertions.assertEquals(7, g2.vertexCount())
//        Assertions.assertEquals(6, g2.edgeCount())
//        val g3 = g2.mapEdges { e: Int -> ('A'.code + e).toChar() }
//        Assertions.assertEquals(6, g3.verticesByPath[6])
//        Assertions.assertEquals(1, g3.getEdgesFromPathToPath(5, 6).size)
//        Assertions.assertEquals('F', g3.getEdgesFromPathToPath(5, 6).first())
//    }
//
//    @Test
//    fun filterEdgesTwoToOnePathsToEdgeGraphTest() {
//        val g1 =
//            (0..6).asSequence().fold(
//                PathBasedGraph.emptyTwoToOnePathsToEdgeGraph<Int, Int, Int>()
//                                    ) { acc: PathBasedGraph<Int, Int, Int>, i: Int -> acc.putVertex(i, i) }
//        val g2 =
//            (0..5).fold(g1) { acc: PathBasedGraph<Int, Int, Int>, i: Int ->
//                acc.putEdge(i, i + 1, i)
//            }
//        Assertions.assertEquals(7, g2.vertexCount())
//        Assertions.assertEquals(6, g2.edgeCount())
//        val g3 = g2.filterEdges { e: Int -> e != 5 }
//        Assertions.assertEquals(5, g3.edgeCount())
//        Assertions.assertEquals(6, g3.verticesByPath[6])
//        Assertions.assertEquals(emptySet<Int>(), g3.getEdgesFromPathToPath(5, 6))
//    }
//
//    @Test
//    fun successorsTest() {
//        val vertices: IntRange = 1..12
//        val edges: Sequence<Triple<Int, Int, Int>> =
//            sequenceOf(
//                Triple(1, 2, 1),
//                Triple(2, 3, 1),
//                Triple(2, 6, 1),
//                Triple(3, 4, 1),
//                Triple(3, 5, 1),
//                Triple(1, 7, 1),
//                Triple(1, 8, 1),
//                Triple(8, 9, 1),
//                Triple(8, 12, 1),
//                Triple(9, 10, 1),
//                Triple(9, 11, 1)
//                      )
//        val graph: PathBasedGraph<Int, Int, Int> =
//            edges.fold(
//                vertices.fold(PathBasedGraph.emptyTwoToOnePathsToEdgeGraph()) { g, i ->
//                    g.putVertex(i, i)
//                }
//                      ) { g, e -> g.putEdge(e.first, e.second, e.third) }
//        Assertions.assertEquals(
//            setOf(2 to 2, 7 to 7, 8 to 8),
//            graph.successors(1).asSequence().toSet()
//                               )
//    }
//
//    @Test
//    fun predecessorsTest() {
//        val vertices: IntRange = 1..12
//        val edges: Sequence<Triple<Int, Int, Int>> =
//            sequenceOf(
//                Triple(1, 2, 1),
//                Triple(2, 3, 1),
//                Triple(2, 6, 1),
//                Triple(3, 4, 1),
//                Triple(3, 5, 1),
//                Triple(1, 7, 1),
//                Triple(1, 8, 1),
//                Triple(8, 9, 1),
//                Triple(8, 12, 1),
//                Triple(9, 10, 1),
//                Triple(9, 11, 1)
//                      )
//        val graph: PathBasedGraph<Int, Int, Int> =
//            edges.fold(
//                vertices.fold(PathBasedGraph.emptyTwoToOnePathsToEdgeGraph()) { g, i ->
//                    g.putVertex(i, i)
//                }
//                      ) { g, e -> g.putEdge(e.first, e.second, e.third) }
//        Assertions.assertEquals(setOf(1 to 1), graph.predecessors(8).asSequence().toSet())
//    }

}
