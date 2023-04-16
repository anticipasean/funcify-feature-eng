package funcify.feature.tree

import arrow.core.Option
import funcify.feature.tree.path.TreePath

/**
 *
 * @author smccarron
 * @created 2023-04-05
 */
interface PersistentTree<out V> : ImmutableTree<V> {

    override fun descendent(path: TreePath): Option<PersistentTree<V>>

    override fun descendentsUnder(path: TreePath): Iterable<PersistentTree<V>>

    override fun children(): Iterable<PersistentTree<V>>

    override fun <V1> map(function: (V) -> V1): PersistentTree<V1>

    override fun <V1> bimap(function: (TreePath, V) -> Pair<TreePath, V1>): PersistentTree<V1>

    override fun <V1> bimap(pathMapper: (TreePath) -> TreePath, valueMapper: (V) -> V1): PersistentTree<V1>

    override fun filter(condition: (V) -> Boolean): PersistentTree<V>

    override fun <V1> flatMap(function: (V) -> ImmutableTree<V1>): PersistentTree<V1>

    override fun <V1> biFlatMap(function: (TreePath, V) -> ImmutableTree<V1>): PersistentTree<V1>

    override fun <V1, V2> zip(other: ImmutableTree<V1>, function: (V, V1) -> V2): PersistentTree<V2>
}
