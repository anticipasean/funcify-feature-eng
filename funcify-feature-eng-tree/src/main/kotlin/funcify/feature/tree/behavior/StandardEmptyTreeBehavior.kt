package funcify.feature.tree.behavior

import funcify.feature.tree.data.LeafData
import funcify.feature.tree.data.StandardLeafData
import funcify.feature.tree.data.StandardTreeData.Companion.StandardTreeWT

/**
 *
 * @author smccarron
 * @created 2023-04-18
 */
internal interface StandardEmptyTreeBehavior :
    StandardTreeBehavior, EmptyTreeBehavior<StandardTreeWT> {

    override fun <V> set(value: V): LeafData<StandardTreeWT, V> {
        return StandardLeafData<V>(value = value)
    }
}
