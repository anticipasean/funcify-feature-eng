package funcify.feature.tree.data

import funcify.feature.tree.data.StandardTreeData.Companion.StandardTreeWT

internal class StandardEmptyTreeData<V> : StandardTreeData<V>(), EmptyTreeData<StandardTreeWT, V> {

    companion object {

        inline fun <V> narrow(
            container: EmptyTreeData<StandardTreeWT, V>
        ): StandardEmptyTreeData<V> {
            return container as StandardEmptyTreeData<V>
        }

        inline fun <V> EmptyTreeData<StandardTreeWT, V>.narrowed(): StandardEmptyTreeData<V> {
            return StandardEmptyTreeData.narrow(this)
        }

        private val EMPTY: StandardEmptyTreeData<Nothing> = StandardEmptyTreeData()

        fun <V> getInstance(): StandardEmptyTreeData<V> {
            @Suppress("UNCHECKED_CAST") //
            return EMPTY as StandardEmptyTreeData<V>
        }

    }
}
