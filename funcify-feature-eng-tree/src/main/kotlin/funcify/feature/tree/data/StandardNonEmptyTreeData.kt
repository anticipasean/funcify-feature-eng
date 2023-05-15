package funcify.feature.tree.data

import funcify.feature.tree.data.StandardTreeData.Companion.StandardTreeWT

/**
 *
 * @author smccarron
 * @created 2023-04-17
 */
internal sealed class StandardNonEmptyTreeData<V> :
    StandardTreeData<V>(), NonEmptyTreeData<StandardTreeWT, V> {

    companion object {

        inline fun <V> narrow(
            container: NonEmptyTreeData<StandardTreeWT, V>
        ): StandardNonEmptyTreeData<V> {
            return container as StandardNonEmptyTreeData<V>
        }

        inline fun <V> NonEmptyTreeData<StandardTreeWT, V>.narrowed(): StandardNonEmptyTreeData<V> {
            return StandardNonEmptyTreeData.narrow(this)
        }
    }
}
