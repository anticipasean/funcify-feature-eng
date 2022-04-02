package funcify.feature.tools.container.graph

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test


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
                                        .sum(),
                                "not expected edge sum prior to minimizing")
        val mstGraph: PersistentGraph<Int, Int, Int> = graph.createMinimumSpanningTreeGraphUsingEdgeCostFunction(Comparator.naturalOrder())
        Assertions.assertEquals(14,
                                mstGraph.edges()
                                        .sum(),
                                "not expected edge sum")
    }

}