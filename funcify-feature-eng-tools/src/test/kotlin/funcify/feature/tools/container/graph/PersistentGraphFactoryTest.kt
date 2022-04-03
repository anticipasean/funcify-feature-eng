package funcify.feature.tools.container.graph

import arrow.core.Tuple5
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.stream.Stream
import kotlin.streams.asSequence


/**
 *
 * @author smccarron
 * @created 4/1/22
 */
internal class PersistentGraphFactoryTest {

    @Test
    fun simpleMinimumSpanningTreeTest() {
        val vertices: Sequence<Int> = (0..6).asSequence()
        val edges: Sequence<Triple<Int, Int, Int>> = sequenceOf(Triple(0,
                                                                       1,
                                                                       4),
                                                                Triple(0,
                                                                       2,
                                                                       3),
                                                                Triple(1,
                                                                       2,
                                                                       1),
                                                                Triple(1,
                                                                       3,
                                                                       2),
                                                                Triple(2,
                                                                       3,
                                                                       4),
                                                                Triple(3,
                                                                       4,
                                                                       2),
                                                                Triple(4,
                                                                       5,
                                                                       6))
        val graph: PersistentGraph<Int, Int, Int> = edges.fold(vertices.fold(PersistentGraph.empty()) { g, v ->
            g.putVertex(v,
                        v)
        }) { g, e ->
            g.putEdge(e.first,
                      e.second,
                      e.third)
        }
        Assertions.assertEquals(22,
                                graph.edges()
                                        .mapToInt { i -> i }
                                        .sum(),
                                "not expected edge sum prior to minimizing")
        val mstGraph: PersistentGraph<Int, Int, Int> = graph.createMinimumSpanningTreeGraphUsingEdgeCostFunction(Comparator.naturalOrder())
        Assertions.assertEquals(14,
                                mstGraph.edges()
                                        .mapToInt { i -> i }
                                        .sum(),
                                "not expected edge sum")
    }

    @Test
    fun depthFirstSearchTest() {
        val vertices: IntRange = 1..12
        val edges: Sequence<Triple<Int, Int, Int>> = sequenceOf(Triple(1,
                                                                       2,
                                                                       1),
                                                                Triple(2,
                                                                       3,
                                                                       1),
                                                                Triple(2,
                                                                       6,
                                                                       1),
                                                                Triple(3,
                                                                       4,
                                                                       1),
                                                                Triple(3,
                                                                       5,
                                                                       1),
                                                                Triple(1,
                                                                       7,
                                                                       1),
                                                                Triple(1,
                                                                       8,
                                                                       1),
                                                                Triple(8,
                                                                       9,
                                                                       1),
                                                                Triple(8,
                                                                       12,
                                                                       1),
                                                                Triple(9,
                                                                       10,
                                                                       1),
                                                                Triple(9,
                                                                       11,
                                                                       1))
        val graph: PersistentGraph<Int, Int, Int> = edges.fold(vertices.fold(PersistentGraph.empty<Int, Int, Int>()) { g, i ->
            g.putVertex(i,
                        i)
        }) { g, e ->
            g.putEdge(e.first,
                      e.second,
                      e.third)
        }
        val depthFirstSearch: Stream<Tuple5<Int, Int, Int, Int, Int>> = graph.depthFirstSearchOnPath(1)
        val verticesEncountered = depthFirstSearch.asSequence()
                .map { t -> t.fifth }
                .toMutableList()
                .apply { // insert first vertex since it counts
                    // but never appears in fifth position
                    add(0,
                        1)
                }
                .toList()
        Assertions.assertEquals((1..12).toList(),
                                verticesEncountered,
                                "vertices are not being processed in expected depth-first search order")
    }

}