package funcify.feature.tree.behavior

import arrow.core.Option
import arrow.core.none
import arrow.core.toOption
import funcify.feature.tree.data.ArrayBranchData
import funcify.feature.tree.data.StandardArrayBranchData
import funcify.feature.tree.data.StandardArrayBranchData.Companion.narrowed
import funcify.feature.tree.data.StandardEmptyTreeData
import funcify.feature.tree.data.StandardLeafData
import funcify.feature.tree.data.StandardNonEmptyTreeData
import funcify.feature.tree.data.StandardObjectBranchData
import funcify.feature.tree.data.StandardTreeData
import funcify.feature.tree.data.StandardTreeData.Companion.StandardTreeWT
import funcify.feature.tree.data.StandardTreeData.Companion.narrowed

/**
 *
 * @author smccarron
 * @created 2023-04-18
 */
internal interface StandardArrayBranchBehavior :
    StandardTreeBehavior, ArrayBranchBehavior<StandardTreeWT> {

    override fun <V> set(
        container: ArrayBranchData<StandardTreeWT, V>,
        value: V
    ): ArrayBranchData<StandardTreeWT, V> {
        return StandardArrayBranchData<V>(
            subNodeCount = container.narrowed().subNodeCount,
            value = value,
            children = container.narrowed().children
        )
    }

    override fun <V> contains(container: ArrayBranchData<StandardTreeWT, V>, index: Int): Boolean {
        return index in container.narrowed().children.indices
    }

    override fun <V> get(container: ArrayBranchData<StandardTreeWT, V>, index: Int): Option<V> {
        return when (val child = container.narrowed().children.getOrNull(index)) {
            null -> {
                none()
            }
            else -> {
                when (val st: StandardTreeData<V> = child.narrowed()) {
                    is StandardEmptyTreeData<V> -> {
                        none<V>()
                    }
                    is StandardNonEmptyTreeData<V> -> {
                        when (st) {
                            is StandardLeafData<V> -> st.value.toOption()
                            is StandardArrayBranchData<V> -> st.value.toOption()
                            is StandardObjectBranchData<V> -> st.value.toOption()
                        }
                    }
                }
            }
        }
    }

    override fun <V> prepend(
        container: ArrayBranchData<StandardTreeWT, V>,
        value: V
    ): ArrayBranchData<StandardTreeWT, V> {
        val std: StandardArrayBranchData<V> = container.narrowed()
        return StandardArrayBranchData<V>(
            subNodeCount = std.subNodeCount + 1,
            value = std.value,
            children = std.children.add(0, StandardLeafData<V>(value = value))
        )
    }

    override fun <V> append(
        container: ArrayBranchData<StandardTreeWT, V>,
        value: V
    ): ArrayBranchData<StandardTreeWT, V> {
        val std: StandardArrayBranchData<V> = container.narrowed()
        return StandardArrayBranchData<V>(
            subNodeCount = std.subNodeCount + 1,
            value = std.value,
            children = std.children.add(StandardLeafData<V>(value = value))
        )
    }

    override fun <V> remove(
        container: ArrayBranchData<StandardTreeWT, V>,
        index: Int
    ): ArrayBranchData<StandardTreeWT, V> {
        return when (index) {
            in container.narrowed().children.indices -> {
                val std: StandardArrayBranchData<V> = container.narrowed()
                StandardArrayBranchData<V>(
                    subNodeCount = std.subNodeCount - size(std.children[index]),
                    value = std.value,
                    children = std.children.removeAt(index)
                )
            }
            else -> {
                container
            }
        }
    }
}
