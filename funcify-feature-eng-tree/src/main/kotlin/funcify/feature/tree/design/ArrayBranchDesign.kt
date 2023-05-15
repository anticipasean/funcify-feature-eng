package funcify.feature.tree.design

import arrow.core.Option
import funcify.feature.tree.ArrayBranch
import funcify.feature.tree.behavior.ArrayBranchBehavior
import funcify.feature.tree.data.ArrayBranchData

/**
 *
 * @author smccarron
 * @created 2023-04-17
 */
internal interface ArrayBranchDesign<DWT, V> : PersistentTreeDesign<DWT, V>, ArrayBranch<V> {

    override val behavior: ArrayBranchBehavior<DWT>

    override val data: ArrayBranchData<DWT, V>

    override fun set(value: V): ArrayBranch<V> {
        return arrayBranch(this.behavior.set(container = data, value = value))
    }

    override fun contains(index: Int): Boolean {
        return this.behavior.contains(container = data, index = index)
    }

    override fun get(index: Int): Option<V> {
        return this.behavior.get(container = data, index = index)
    }

    override fun prepend(value: V): ArrayBranch<V> {
        return arrayBranch(this.behavior.prepend(container = data, value = value))
    }

    override fun append(value: V): ArrayBranch<V> {
        return arrayBranch(this.behavior.append(container = data, value = value))
    }

    override fun remove(index: Int): ArrayBranch<V> {
        return arrayBranch(this.behavior.remove(container = data, index = index))
    }
}
