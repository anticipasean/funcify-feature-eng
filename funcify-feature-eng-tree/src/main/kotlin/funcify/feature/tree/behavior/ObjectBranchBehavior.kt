package funcify.feature.tree.behavior

import arrow.core.Option
import funcify.feature.tree.data.ObjectBranchData

/**
 *
 * @author smccarron
 * @created 2023-04-18
 */
internal interface ObjectBranchBehavior<DWT> : TreeBehavior<DWT> {

    fun <V> contains(container: ObjectBranchData<DWT, V>, name: String): Boolean

    fun <V> get(container: ObjectBranchData<DWT, V>, name: String): Option<V>

    fun <V> put(
        container: ObjectBranchData<DWT, V>,
        name: String,
        value: V
    ): ObjectBranchData<DWT, V>

    fun <V> remove(container: ObjectBranchData<DWT, V>, name: String): ObjectBranchData<DWT, V>
}
