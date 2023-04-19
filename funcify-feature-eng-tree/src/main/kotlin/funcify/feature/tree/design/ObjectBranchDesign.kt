package funcify.feature.tree.design

import arrow.core.Option
import funcify.feature.tree.ArrayBranch
import funcify.feature.tree.Leaf
import funcify.feature.tree.ObjectBranch
import funcify.feature.tree.behavior.ObjectBranchBehavior
import funcify.feature.tree.data.ObjectBranchData

/**
 *
 * @author smccarron
 * @created 2023-04-17
 */
internal interface ObjectBranchDesign<DWT, V> : PersistentTreeDesign<DWT, V>, ObjectBranch<V> {

    override val behavior: ObjectBranchBehavior<DWT>

    override val data: ObjectBranchData<DWT, V>

    override fun <R> fold(
        leafHandler: (Leaf<V>) -> R,
        arrayTreeHandler: (ArrayBranch<V>) -> R,
        objectTreeHandler: (ObjectBranch<V>) -> R
    ): R {
        return objectTreeHandler.invoke(this)
    }

    override fun contains(name: String): Boolean {
        return this.behavior.contains(container = data, name = name)
    }

    override fun get(name: String): Option<V> {
        return this.behavior.get(container = data, name = name)
    }

    override fun put(name: String, value: V): ObjectBranch<V> {
        return objectBranch(this.behavior.put(container = data, name = name, value = value))
    }

    override fun remove(name: String): ObjectBranch<V> {
        return objectBranch(this.behavior.remove(container = data, name = name))
    }
}
