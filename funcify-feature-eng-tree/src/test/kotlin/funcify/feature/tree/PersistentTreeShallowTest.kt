package funcify.feature.tree

import funcify.feature.tree.path.TreePath
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 *
 * @author smccarron
 * @created 2023-05-16
 */
class PersistentTreeShallowTest {

    @Test
    fun createLeafFromEmptyTest() {
        val emptyTree: EmptyTree<String> =
            Assertions.assertDoesNotThrow<EmptyTree<String>> { PersistentTree.empty<String>() }
        val bobLeaf: Leaf<String> = emptyTree.set("Bob")
        Assertions.assertEquals("Bob", bobLeaf.value().orNull()) { "value was not set to Bob" }
        val maryLeaf = bobLeaf.set("Mary")
        Assertions.assertEquals("Bob", bobLeaf.value().orNull()) {
            "bob leaf is not still set to Bob, so implementation not immutable"
        }
        Assertions.assertEquals("Mary", maryLeaf.value().orNull()) { "value was not set to Mary" }
        val bobLeafIterator: Iterator<Pair<TreePath, String>> = bobLeaf.breadthFirstIterator()
        Assertions.assertTrue(bobLeafIterator.hasNext()) { "bobLeafIterator should have one pair" }
        Assertions.assertEquals(TreePath.getRootPath() to "Bob", bobLeafIterator.next()) {
            "bobLeafIterator yielded unexpected pair value"
        }
    }
}
