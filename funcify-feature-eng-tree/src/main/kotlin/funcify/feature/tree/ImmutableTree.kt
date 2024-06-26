package funcify.feature.tree

import arrow.core.Option
import funcify.feature.tree.path.TreePath

/**
 *
 * @author smccarron
 * @created 2023-04-05
 */
interface ImmutableTree<out V> : Iterable<Pair<TreePath, V>> {

    fun value(): Option<V>

    operator fun contains(path: TreePath): Boolean

    operator fun get(path: TreePath): Option<ImmutableTree<V>>

    fun descendentsUnder(path: TreePath): Iterable<ImmutableTree<V>>

    fun size(): Int

    fun children(): Iterable<ImmutableTree<V>>

    fun levels(): Iterable<Pair<Int, Iterable<Pair<TreePath, V>>>>

    fun <V1> map(function: (V) -> V1): ImmutableTree<V1>

    fun <V1> bimap(function: (TreePath, V) -> Pair<TreePath, V1>): ImmutableTree<V1>

    fun <V1> bimap(pathMapper: (TreePath) -> TreePath, valueMapper: (V) -> V1): ImmutableTree<V1>

    fun filter(condition: (V) -> Boolean): ImmutableTree<V>

    fun biFilter(condition: (TreePath, V) -> Boolean): ImmutableTree<V>

    fun <V1> flatMap(function: (V) -> ImmutableTree<V1>): ImmutableTree<V1>

    fun <V1> biFlatMap(function: (TreePath, V) -> ImmutableTree<V1>): ImmutableTree<V1>

    fun <V1, V2> zip(other: ImmutableTree<V1>, function: (V, V1) -> V2): ImmutableTree<V2>

    fun <V1, V2> biZip(
        other: ImmutableTree<V1>,
        function: (Pair<TreePath, V>, Pair<TreePath, V1>) -> Pair<TreePath, V2>
    ): ImmutableTree<V2>

    fun <R> foldLeft(startValue: R, accumulator: (R, V) -> R): R

    fun <R> biFoldLeft(startValue: R, accumulator: (R, TreePath, V) -> R): R

    fun breadthFirstIterator(): Iterator<Pair<TreePath, V>>

    fun depthFirstIterator(): Iterator<Pair<TreePath, V>>

    override fun iterator(): Iterator<Pair<TreePath, V>> {
        return breadthFirstIterator()
    }
}
