package funcify.feature.tree.context

import funcify.feature.tree.behavior.ArrayBranchBehavior
import funcify.feature.tree.behavior.EmptyTreeBehavior
import funcify.feature.tree.behavior.LeafBehavior
import funcify.feature.tree.behavior.ObjectBranchBehavior
import funcify.feature.tree.behavior.TreeBehaviorFactory
import funcify.feature.tree.data.ArrayBranchData
import funcify.feature.tree.data.EmptyTreeData
import funcify.feature.tree.data.LeafData
import funcify.feature.tree.data.ObjectBranchData
import funcify.feature.tree.data.StandardEmptyTreeData
import funcify.feature.tree.data.StandardTreeData.Companion.StandardTreeWT
import funcify.feature.tree.design.ArrayBranchDesign
import funcify.feature.tree.design.EmptyTreeDesign
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

        internal data class StandardEmptyTreeContext<V>(
            override val behavior: EmptyTreeBehavior<StandardTreeWT> =
                TreeBehaviorFactory.getStandardEmptyTreeBehavior(),
            override val data: EmptyTreeData<StandardTreeWT, V> =
                StandardEmptyTreeData.getInstance()
        ) : StandardTreeContext<V>(), EmptyTreeDesign<StandardTreeWT, V>

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

        private val EMPTY: StandardEmptyTreeContext<Nothing> by lazy {
            StandardEmptyTreeContext<Nothing>()
        }

        fun <V> empty(): StandardEmptyTreeContext<V> {
            @Suppress("UNCHECKED_CAST") //
            return EMPTY as StandardEmptyTreeContext<V>
        }
    }

    override fun <V> empty(): EmptyTreeDesign<StandardTreeWT, V> {
        @Suppress("UNCHECKED_CAST") //
        return EMPTY as StandardEmptyTreeContext<V>
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
