package funcify.feature.tree.design

import funcify.feature.tree.ArrayBranch
import funcify.feature.tree.Leaf
import funcify.feature.tree.ObjectBranch
import funcify.feature.tree.behavior.LeafBehavior
import funcify.feature.tree.data.LeafData

/**
 *
 * @author smccarron
 * @created 2023-04-17
 */
internal interface LeafDesign<DWT, V> : PersistentTreeDesign<DWT, V>, Leaf<V> {

    override val behavior: LeafBehavior<DWT>

    override val data: LeafData<DWT, V>

    override fun set(value: V): Leaf<V> {
        return leaf(this.behavior.set(container = data, value = value))
    }

    override fun put(name: String, value: V): ObjectBranch<V> {
        return objectBranch(this.behavior.put(container = data, name = name, value = value))
    }

    override fun append(value: V): ArrayBranch<V> {
        return arrayBranch(this.behavior.append(container = data, value = value))
    }

    override fun prepend(value: V): ArrayBranch<V> {
        return arrayBranch(this.behavior.prepend(container = data, value = value))
    }
}
