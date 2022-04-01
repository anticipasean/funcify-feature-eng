package funcify.feature.tools.container.tree

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test


/**
 *
 * @author smccarron
 * @created 4/1/22
 */
internal class UnionFindTreeTest {

    @Test
    fun simpleUnionFindTest() {
        val coordinates = sequenceOf<Pair<Int, Int>>(Pair(3,
                                                          4),
                                                     Pair(4,
                                                          9),
                                                     Pair(8,
                                                          0),
                                                     Pair(2,
                                                          3),
                                                     Pair(5,
                                                          6),
                                                     Pair(5,
                                                          9),
                                                     Pair(7,
                                                          3),
                                                     Pair(4,
                                                          8),
                                                     Pair(6,
                                                          1))

        val initialUnionFindTree: UnionFindTree<Int> = (0..10).fold(UnionFindTree.empty()) { uf, i -> uf.add(i) }
        val updatedUnionFind: UnionFindTree<Int> = coordinates.fold(initialUnionFindTree) { uf, pair ->
            uf.union(pair.first,
                     pair.second)
        }
        Assertions.assertEquals(3,
                                updatedUnionFind.parents.getOrDefault(1,
                                                                      -1),
                                "vertex_1 does not have the expected parent")
    }

}