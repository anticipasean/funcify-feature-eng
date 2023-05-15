package funcify.feature.tree

/**
 *
 * @author smccarron
 * @created 2023-04-16
 */
interface NonEmptyTree<out V> : PersistentTree<V> {

    override fun <R> fold(
        emptyHandler: (EmptyTree<V>) -> R,
        nonEmptyHandler: (NonEmptyTree<V>) -> R
    ): R {
        return nonEmptyHandler.invoke(this)
    }

    fun <R> fold(
        leafHandler: (Leaf<V>) -> R,
        arrayBranchHandler: (ArrayBranch<V>) -> R,
        objectBranchHandler: (ObjectBranch<V>) -> R
    ): R
}
