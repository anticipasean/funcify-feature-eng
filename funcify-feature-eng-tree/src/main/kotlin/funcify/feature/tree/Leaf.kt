package funcify.feature.tree

/**
 *
 * @author smccarron
 * @created 2023-04-16
 */
interface Leaf<out V>: PersistentTree<V> {

    fun put(name: String, value: @UnsafeVariance V): ObjectBranch<V>

    fun append(value: @UnsafeVariance V): ArrayBranch<V>

    fun prepend(value: @UnsafeVariance V): ArrayBranch<V>

}
