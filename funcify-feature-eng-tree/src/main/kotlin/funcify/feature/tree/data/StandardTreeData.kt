package funcify.feature.tree.data

import funcify.feature.tree.data.StandardTreeData.Companion.StandardTreeWT

/**
 *
 * @author smccarron
 * @created 2023-04-17
 */
internal sealed class StandardTreeData<V> : TreeData<StandardTreeWT, V> {

    companion object {
        enum class StandardTreeWT

        inline fun <V> narrow(container: TreeData<StandardTreeWT, V>): StandardTreeData<V> {
            return container as StandardTreeData<V>
        }

        inline fun <V> TreeData<StandardTreeWT, V>.narrowed(): StandardTreeData<V> {
            return StandardTreeData.narrow(this)
        }
    }
}
