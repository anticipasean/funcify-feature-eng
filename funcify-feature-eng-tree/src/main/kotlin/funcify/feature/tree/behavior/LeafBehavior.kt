package funcify.feature.tree.behavior

import funcify.feature.tree.data.ArrayBranchData
import funcify.feature.tree.data.LeafData
import funcify.feature.tree.data.ObjectBranchData

/**
 *
 * @author smccarron
 * @created 2023-04-18
 */
internal interface LeafBehavior<DWT> : TreeBehavior<DWT> {

    fun <V> set(container: LeafData<DWT, V>, value: V): LeafData<DWT, V>

    fun <V> put(container: LeafData<DWT, V>, name: String, value: V): ObjectBranchData<DWT, V>

    fun <V> append(container: LeafData<DWT, V>, value: V): ArrayBranchData<DWT, V>

    fun <V> prepend(container: LeafData<DWT, V>, value: V): ArrayBranchData<DWT, V>
}
