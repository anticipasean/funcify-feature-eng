package funcify.feature.tree

import arrow.core.Option

/**
 *
 * @author smccarron
 * @created 2023-04-16
 */
interface ObjectBranch<out V> : NonEmptyTree<V> {

    operator fun contains(name: String): Boolean

    operator fun get(name: String): Option<V>

    fun set(value: @UnsafeVariance V): ObjectBranch<V>

    fun put(name: String, value: @UnsafeVariance V): ObjectBranch<V>

    fun remove(name: String): ObjectBranch<V>

    override fun <R> fold(
        leafHandler: (Leaf<V>) -> R,
        arrayBranchHandler: (ArrayBranch<V>) -> R,
        objectBranchHandler: (ObjectBranch<V>) -> R
    ): R {
        return objectBranchHandler.invoke(this)
    }
}
