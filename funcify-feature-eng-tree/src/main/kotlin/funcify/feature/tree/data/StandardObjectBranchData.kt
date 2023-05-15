package funcify.feature.tree.data

import funcify.feature.tree.data.StandardTreeData.Companion.StandardTreeWT
import funcify.feature.tree.path.PathSegment
import kotlinx.collections.immutable.PersistentMap

internal data class StandardObjectBranchData<V>(
    val value: V?,
    val children: PersistentMap<String, StandardNonEmptyTreeData<V>>
) : StandardNonEmptyTreeData<V>(), ObjectBranchData<StandardTreeWT, V> {

    companion object {

        inline fun <V> narrow(
            container: ObjectBranchData<StandardTreeWT, V>
        ): StandardObjectBranchData<V> {
            return container as StandardObjectBranchData<V>
        }

        inline fun <V> ObjectBranchData<StandardTreeWT, V>.narrowed(): StandardObjectBranchData<V> {
            return StandardObjectBranchData.narrow(this)
        }
    }
}
