package funcify.feature.tree

import arrow.core.Option
import funcify.feature.tree.path.TreePath

/**
 *
 * @author smccarron
 * @created 2023-04-05
 */
interface ImmutableTree<out V> {

    fun value(): Option<V>

    operator fun contains(path: TreePath): Boolean

    operator fun get(path: TreePath): Option<ImmutableTree<V>>

    fun descendentsUnder(path: TreePath): Iterable<ImmutableTree<V>>

    fun children(): Iterable<ImmutableTree<V>>

    fun <V1> map(function: (V) -> V1): ImmutableTree<V1>

    fun <V1> bimap(function: (TreePath, V) -> Pair<TreePath, V1>): ImmutableTree<V1>

    fun <V1> bimap(pathMapper: (TreePath) -> TreePath, valueMapper: (V) -> V1): ImmutableTree<V1>

    fun filter(condition: (V) -> Boolean): ImmutableTree<V>

    fun <V1> flatMap(function: (V) -> ImmutableTree<V1>): ImmutableTree<V1>

    fun <V1> biFlatMap(function: (TreePath, V) -> ImmutableTree<V1>): ImmutableTree<V1>

    fun <V1, V2> zip(other: ImmutableTree<V1>, function: (V, V1) -> V2): ImmutableTree<V2>

    fun <R> foldLeft(startValue: R, accumulator: (R, V) -> R): R

    fun <R> biFoldLeft(startValue: R, accumulator: (R, TreePath, V) -> R): R

    fun depthFirstIterator(): Iterator<Pair<TreePath, V>>

    fun breadthFirstIterator(): Iterator<Pair<TreePath, V>>

    fun <R> fold(
        leafHandler: (Leaf<V>) -> R,
        arrayTreeHandler: (ArrayBranch<V>) -> R,
        objectTreeHandler: (ObjectBranch<V>) -> R
    ): R
}
