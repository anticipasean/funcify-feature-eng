package funcify.feature.tree

import arrow.core.Option

/**
 *
 * @author smccarron
 * @created 2023-04-16
 */
interface ArrayBranch<out V> : PersistentTree<V> {

    fun contains(index: Int): Boolean

    fun get(index: Int): Option<V>

    fun prepend(value: @UnsafeVariance V): ArrayBranch<V>

    fun append(value: @UnsafeVariance V): ArrayBranch<V>

    fun remove(index: Int): ArrayBranch<V>
}