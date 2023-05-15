package funcify.feature.tree

import arrow.core.Option

/**
 *
 * @author smccarron
 * @created 2023-04-16
 */
interface ArrayBranch<out V> : NonEmptyTree<V> {

    operator fun contains(index: Int): Boolean

    operator fun get(index: Int): Option<V>

    fun set(value: @UnsafeVariance V): ArrayBranch<V>

    fun prepend(value: @UnsafeVariance V): ArrayBranch<V>

    fun append(value: @UnsafeVariance V): ArrayBranch<V>

    fun remove(index: Int): ArrayBranch<V>

    override fun <R> fold(
        leafHandler: (Leaf<V>) -> R,
        arrayBranchHandler: (ArrayBranch<V>) -> R,
        objectBranchHandler: (ObjectBranch<V>) -> R
    ): R {
        return arrayBranchHandler.invoke(this)
    }
}
