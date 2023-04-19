package funcify.feature.tree.data

import funcify.feature.tree.data.StandardTreeData.Companion.StandardTreeWT
import funcify.feature.tree.path.NameSegment

/**
 *
 * @author smccarron
 * @created 2023-04-17
 */
internal sealed class StandardTreeData<V> : TreeData<StandardTreeWT, V> {

    companion object {
        enum class StandardTreeWT

        fun <V> narrow(container: TreeData<StandardTreeWT, V>): StandardTreeData<V> {
            return container as StandardTreeData<V>
        }

        fun <V> TreeData<StandardTreeWT, V>.narrowed(): StandardTreeData<V> {
            return StandardTreeData.narrow(this)
        }

        private val ROOT: StandardLeafData<Any?> by lazy {
            StandardLeafData<Any?>(value = null)
        }

        fun <V> getRoot(): StandardLeafData<V> {
            @Suppress("UNCHECKED_CAST") //
            return ROOT as StandardLeafData<V>
        }

    }

}
