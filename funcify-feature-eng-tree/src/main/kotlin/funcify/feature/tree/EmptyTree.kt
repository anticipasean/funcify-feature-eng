package funcify.feature.tree

/**
 *
 * @author smccarron
 * @created 2023-04-16
 */
interface EmptyTree<out V> : PersistentTree<V> {

    fun set(value: @UnsafeVariance V): Leaf<V>

    override fun <R> fold(
        emptyHandler: (EmptyTree<V>) -> R,
        nonEmptyHandler: (NonEmptyTree<V>) -> R
    ): R {
        return emptyHandler.invoke(this)
    }
}
