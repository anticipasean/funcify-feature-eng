package funcify.feature.tree

import arrow.core.Option

/**
 *
 * @author smccarron
 * @created 2023-04-16
 */
interface ObjectBranch<out V> : PersistentTree<V> {

    fun contains(name: String): Boolean

    fun get(name: String): Option<V>

    fun put(name: String, value: @UnsafeVariance V): ObjectBranch<V>

    fun remove(name: String): ObjectBranch<V>
}
