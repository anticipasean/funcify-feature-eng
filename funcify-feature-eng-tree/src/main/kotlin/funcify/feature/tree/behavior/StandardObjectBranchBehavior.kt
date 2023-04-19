package funcify.feature.tree.behavior

import arrow.core.Option
import arrow.core.none
import arrow.core.toOption
import funcify.feature.tree.data.ObjectBranchData
import funcify.feature.tree.data.StandardArrayBranchData
import funcify.feature.tree.data.StandardLeafData
import funcify.feature.tree.data.StandardObjectBranchData
import funcify.feature.tree.data.StandardObjectBranchData.Companion.narrowed
import funcify.feature.tree.data.StandardTreeData
import funcify.feature.tree.data.StandardTreeData.Companion.StandardTreeWT
import funcify.feature.tree.data.StandardTreeData.Companion.narrowed

/**
 *
 * @author smccarron
 * @created 2023-04-18
 */
internal interface StandardObjectBranchBehavior :
    StandardTreeBehavior, ObjectBranchBehavior<StandardTreeWT> {

    override fun <V> contains(
        container: ObjectBranchData<StandardTreeWT, V>,
        name: String
    ): Boolean {
        return container.narrowed().children.containsKey(name)
    }

    override fun <V> get(container: ObjectBranchData<StandardTreeWT, V>, name: String): Option<V> {
        return when (val child = container.narrowed().children[name]) {
            null -> {
                none()
            }
            else -> {
                when (val st: StandardTreeData<V> = child.narrowed()) {
                    is StandardLeafData<V> -> st.value.toOption()
                    is StandardArrayBranchData<V> -> st.value.toOption()
                    is StandardObjectBranchData<V> -> st.value.toOption()
                }
            }
        }
    }

    override fun <V> put(
        container: ObjectBranchData<StandardTreeWT, V>,
        name: String,
        value: V
    ): ObjectBranchData<StandardTreeWT, V> {
        return StandardObjectBranchData<V>(
            value = container.narrowed().value,
            children = container.narrowed().children.put(name, StandardLeafData<V>(value = value))
        )
    }

    override fun <V> remove(
        container: ObjectBranchData<StandardTreeWT, V>,
        name: String
    ): ObjectBranchData<StandardTreeWT, V> {
        return StandardObjectBranchData<V>(
            value = container.narrowed().value,
            children = container.narrowed().children.remove(name)
        )
    }
}
