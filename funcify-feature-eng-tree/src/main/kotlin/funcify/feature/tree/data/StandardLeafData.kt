package funcify.feature.tree.data

import funcify.feature.tree.data.StandardTreeData.Companion.StandardTreeWT

internal data class StandardLeafData<V>(val value: V?) :
    StandardNonEmptyTreeData<V>(), LeafData<StandardTreeWT, V> {

    companion object {

        inline fun <V> narrow(container: LeafData<StandardTreeWT, V>): StandardLeafData<V> {
            return container as StandardLeafData<V>
        }

        inline fun <V> LeafData<StandardTreeWT, V>.narrowed(): StandardLeafData<V> {
            return StandardLeafData.narrow(this)
        }
    }
}
