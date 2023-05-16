package funcify.feature.tree.behavior

import funcify.feature.tree.data.ArrayBranchData
import funcify.feature.tree.data.LeafData
import funcify.feature.tree.data.ObjectBranchData
import funcify.feature.tree.data.StandardArrayBranchData
import funcify.feature.tree.data.StandardLeafData
import funcify.feature.tree.data.StandardLeafData.Companion.narrowed
import funcify.feature.tree.data.StandardObjectBranchData
import funcify.feature.tree.data.StandardTreeData.Companion.StandardTreeWT
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/**
 *
 * @author smccarron
 * @created 2023-04-18
 */
internal interface StandardLeafBehavior : StandardTreeBehavior, LeafBehavior<StandardTreeWT> {

    override fun <V> set(
        container: LeafData<StandardTreeWT, V>,
        value: V
    ): LeafData<StandardTreeWT, V> {
        return StandardLeafData<V>(value = value)
    }

    override fun <V> put(
        container: LeafData<StandardTreeWT, V>,
        name: String,
        value: V
    ): ObjectBranchData<StandardTreeWT, V> {
        return StandardObjectBranchData<V>(
            subNodeCount = 1,
            value = container.narrowed().value,
            children = persistentMapOf(name to StandardLeafData(value = value))
        )
    }

    override fun <V> append(
        container: LeafData<StandardTreeWT, V>,
        value: V
    ): ArrayBranchData<StandardTreeWT, V> {
        return StandardArrayBranchData<V>(
            subNodeCount = 1,
            value = container.narrowed().value,
            children = persistentListOf(StandardLeafData<V>(value = value))
        )
    }

    override fun <V> prepend(
        container: LeafData<StandardTreeWT, V>,
        value: V
    ): ArrayBranchData<StandardTreeWT, V> {
        return StandardArrayBranchData<V>(
            subNodeCount = 1,
            value = container.narrowed().value,
            children = persistentListOf(StandardLeafData<V>(value = value))
        )
    }
}
