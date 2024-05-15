package funcify.feature.tree

/**
 *
 * @author smccarron
 * @created 2023-04-16
 */
interface Leaf<out V> : NonEmptyTree<V> {

    fun set(value: @UnsafeVariance V): Leaf<V>

    fun put(name: String, value: @UnsafeVariance V): ObjectBranch<V>

    fun append(value: @UnsafeVariance V): ArrayBranch<V>

    fun prepend(value: @UnsafeVariance V): ArrayBranch<V>

    override fun <R> fold(
        leafHandler: (Leaf<V>) -> R,
        arrayBranchHandler: (ArrayBranch<V>) -> R,
        objectBranchHandler: (ObjectBranch<V>) -> R
    ): R {
        return leafHandler.invoke(this)
    }
}
