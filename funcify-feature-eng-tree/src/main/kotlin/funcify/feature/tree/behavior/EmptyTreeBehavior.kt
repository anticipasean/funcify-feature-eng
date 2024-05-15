package funcify.feature.tree.behavior

import funcify.feature.tree.data.LeafData

/**
 *
 * @author smccarron
 * @created 2023-04-18
 */
internal interface EmptyTreeBehavior<DWT> : TreeBehavior<DWT> {

    fun <V> set(value: V): LeafData<DWT, V>
}
