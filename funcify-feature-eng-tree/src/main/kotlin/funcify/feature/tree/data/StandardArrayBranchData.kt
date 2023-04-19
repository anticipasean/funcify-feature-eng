package funcify.feature.tree.data

import funcify.feature.tree.data.StandardTreeData.Companion.StandardTreeWT
import funcify.feature.tree.path.PathSegment
import kotlinx.collections.immutable.PersistentList

internal data class StandardArrayBranchData<V>(
    val value: V?,
    val children: PersistentList<StandardTreeData<V>>
) : StandardTreeData<V>(), ArrayBranchData<StandardTreeWT, V> {

    companion object {
        fun <V> narrow(container: ArrayBranchData<StandardTreeWT, V>): StandardArrayBranchData<V> {
            return container as StandardArrayBranchData<V>
        }

        fun <V> ArrayBranchData<StandardTreeWT, V>.narrowed(): StandardArrayBranchData<V> {
            return StandardArrayBranchData.narrow(this)
        }
    }
}
