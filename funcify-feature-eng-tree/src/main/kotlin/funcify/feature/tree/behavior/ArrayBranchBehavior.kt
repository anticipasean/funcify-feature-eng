package funcify.feature.tree.behavior

import arrow.core.Option
import funcify.feature.tree.data.ArrayBranchData

/**
 *
 * @author smccarron
 * @created 2023-04-18
 */
internal interface ArrayBranchBehavior<DWT> : TreeBehavior<DWT> {

    fun <V> contains(container: ArrayBranchData<DWT, V>, index: Int): Boolean

    fun <V> get(container: ArrayBranchData<DWT, V>, index: Int): Option<V>

    fun <V> prepend(container: ArrayBranchData<DWT, V>, value: V): ArrayBranchData<DWT, V>

    fun <V> append(container: ArrayBranchData<DWT, V>, value: V): ArrayBranchData<DWT, V>

    fun <V> remove(container: ArrayBranchData<DWT, V>, index: Int): ArrayBranchData<DWT, V>
}
