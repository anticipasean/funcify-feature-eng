package funcify.feature.tree.design

import funcify.feature.tree.EmptyTree
import funcify.feature.tree.Leaf
import funcify.feature.tree.behavior.EmptyTreeBehavior
import funcify.feature.tree.data.ArrayBranchData
import funcify.feature.tree.data.EmptyTreeData
import funcify.feature.tree.data.LeafData
import funcify.feature.tree.data.ObjectBranchData

/**
 *
 * @author smccarron
 * @created 2023-04-17
 */
internal interface EmptyTreeDesign<DWT, V> : PersistentTreeDesign<DWT, V>, EmptyTree<V> {

    override val behavior: EmptyTreeBehavior<DWT>

    override val data: EmptyTreeData<DWT, V>

    override fun <V> empty(): EmptyTreeDesign<DWT, V>

    override fun <V> leaf(data: LeafData<DWT, V>): LeafDesign<DWT, V>

    override fun <V> arrayBranch(data: ArrayBranchData<DWT, V>): ArrayBranchDesign<DWT, V>

    override fun <V> objectBranch(data: ObjectBranchData<DWT, V>): ObjectBranchDesign<DWT, V>

    override fun set(value: V): Leaf<V> {
        return leaf(this.behavior.set(value))
    }
}
