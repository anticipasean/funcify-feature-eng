package funcify.feature.tree.context

import funcify.feature.tree.behavior.ArrayBranchBehavior
import funcify.feature.tree.behavior.LeafBehavior
import funcify.feature.tree.behavior.ObjectBranchBehavior
import funcify.feature.tree.behavior.TreeBehaviorFactory
import funcify.feature.tree.data.ArrayBranchData
import funcify.feature.tree.data.LeafData
import funcify.feature.tree.data.ObjectBranchData
import funcify.feature.tree.data.StandardTreeData
import funcify.feature.tree.data.StandardTreeData.Companion.StandardTreeWT
import funcify.feature.tree.design.ArrayBranchDesign
import funcify.feature.tree.design.LeafDesign
import funcify.feature.tree.design.ObjectBranchDesign
import funcify.feature.tree.design.PersistentTreeDesign

/**
 *
 * @author smccarron
 * @created 2023-04-17
 */
internal sealed class StandardTreeContext<V> : PersistentTreeDesign<StandardTreeWT, V> {

    companion object {

        internal data class StandardObjectBranchContext<V>(
            override val behavior: ObjectBranchBehavior<StandardTreeWT> =
                TreeBehaviorFactory.getStandardObjectBranchBehavior(),
            override val data: ObjectBranchData<StandardTreeWT, V>
        ) : StandardTreeContext<V>(), ObjectBranchDesign<StandardTreeWT, V> {}

        internal data class StandardArrayBranchContext<V>(
            override val behavior: ArrayBranchBehavior<StandardTreeWT> =
                TreeBehaviorFactory.getStandardArrayBranchBehavior(),
            override val data: ArrayBranchData<StandardTreeWT, V>
        ) : StandardTreeContext<V>(), ArrayBranchDesign<StandardTreeWT, V> {}

        internal data class StandardLeafContext<V>(
            override val behavior: LeafBehavior<StandardTreeWT> =
                TreeBehaviorFactory.getStandardLeafBehavior(),
            override val data: LeafData<StandardTreeWT, V>
        ) : StandardTreeContext<V>(), LeafDesign<StandardTreeWT, V> {}

        private val ROOT: StandardLeafContext<Any?> by lazy {
            StandardLeafContext<Any?>(data = StandardTreeData.getRoot<Any?>())
        }

        fun <V> getRoot(): StandardLeafContext<V> {
            @Suppress("UNCHECKED_CAST") //
            return ROOT as StandardLeafContext<V>
        }
    }

    override fun <V> leaf(data: LeafData<StandardTreeWT, V>): LeafDesign<StandardTreeWT, V> {
        return StandardLeafContext<V>(data = data)
    }

    override fun <V> arrayBranch(
        data: ArrayBranchData<StandardTreeWT, V>
    ): ArrayBranchDesign<StandardTreeWT, V> {
        return StandardArrayBranchContext<V>(data = data)
    }

    override fun <V> objectBranch(
        data: ObjectBranchData<StandardTreeWT, V>
    ): ObjectBranchDesign<StandardTreeWT, V> {
        return StandardObjectBranchContext<V>(data = data)
    }
}
