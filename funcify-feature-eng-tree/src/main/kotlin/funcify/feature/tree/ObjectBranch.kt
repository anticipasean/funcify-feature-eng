package funcify.feature.tree

import arrow.core.Option

/**
 *
 * @author smccarron
 * @created 2023-04-16
 */
interface ObjectBranch<out V> : PersistentTree<V> {

    operator fun contains(name: String): Boolean

    operator fun get(name: String): Option<V>

    fun put(name: String, value: @UnsafeVariance V): ObjectBranch<V>

    fun remove(name: String): ObjectBranch<V>
}
