package funcify.feature.materializer.graph

import funcify.feature.tree.path.TreePath
import kotlinx.collections.immutable.ImmutableSet

/**
 * @author smccarron
 * @created 2023-08-04
 */
sealed interface RawInputContextShape {

    interface Tabular : RawInputContextShape {

        val columnSet: ImmutableSet<String>
    }

    interface Tree : RawInputContextShape {

        val treePathSet: ImmutableSet<TreePath>

        val fieldNames: ImmutableSet<String>
    }
}
