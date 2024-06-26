package funcify.feature.tree.behavior

import arrow.core.Option
import com.sun.source.tree.Tree
import funcify.feature.tree.ImmutableTree
import funcify.feature.tree.data.ArrayBranchData
import funcify.feature.tree.data.LeafData
import funcify.feature.tree.data.ObjectBranchData
import funcify.feature.tree.data.TreeData
import funcify.feature.tree.path.TreePath

/**
 *
 * @author smccarron
 * @created 2023-04-17
 */
internal interface TreeBehavior<DWT> {

    fun <V> fromSequence(sequence: Sequence<Pair<TreePath, V>>): TreeData<DWT, V>

    fun <V> value(container: TreeData<DWT, V>): Option<V>

    fun <V> contains(container: TreeData<DWT, V>, path: TreePath): Boolean

    fun <V> get(container: TreeData<DWT, V>, path: TreePath): Option<TreeData<DWT, V>>

    fun <V, R> foldLeft(container: TreeData<DWT, V>, startValue: R, accumulator: (R, V) -> R): R

    fun <V, R> biFoldLeft(
        container: TreeData<DWT, V>,
        startValue: R,
        accumulator: (R, TreePath, V) -> R
    ): R

    fun <V> depthFirstIterator(container: TreeData<DWT, V>): Iterator<Pair<TreePath, V>>

    fun <V> breadthFirstIterator(container: TreeData<DWT, V>): Iterator<Pair<TreePath, V>>

    fun <V> descendentsUnder(
        container: TreeData<DWT, V>,
        path: TreePath
    ): Iterable<TreeData<DWT, V>>

    fun <V> size(container: TreeData<DWT, V>): Int

    fun <V> children(container: TreeData<DWT, V>): Iterable<TreeData<DWT, V>>

    fun <V> levels(container: TreeData<DWT, V>): Iterable<Pair<Int, Iterable<Pair<TreePath, V>>>>

    fun <V, V1> map(container: TreeData<DWT, V>, function: (V) -> V1): TreeData<DWT, V1>

    fun <V, V1> bimap(
        container: TreeData<DWT, V>,
        function: (TreePath, V) -> Pair<TreePath, V1>
    ): TreeData<DWT, V1>

    fun <V, V1> bimap(
        container: TreeData<DWT, V>,
        pathMapper: (TreePath) -> TreePath,
        valueMapper: (V) -> V1
    ): TreeData<DWT, V1>

    fun <V> filter(container: TreeData<DWT, V>, condition: (V) -> Boolean): TreeData<DWT, V>

    fun <V> biFilter(
        container: TreeData<DWT, V>,
        condition: (TreePath, V) -> Boolean
    ): TreeData<DWT, V>

    fun <V, V1> flatMap(
        container: TreeData<DWT, V>,
        function: (V) -> ImmutableTree<V1>
    ): TreeData<DWT, V1>

    fun <V, V1> biFlatMap(
        container: TreeData<DWT, V>,
        function: (TreePath, V) -> ImmutableTree<V1>
    ): TreeData<DWT, V1>

    fun <V, V1, V2> zip(
        container: TreeData<DWT, V>,
        other: ImmutableTree<V1>,
        function: (V, V1) -> V2
    ): TreeData<DWT, V2>

    fun <V, V1, V2> biZip(
        container: TreeData<DWT, V>,
        other: ImmutableTree<V1>,
        function: (Pair<TreePath, V>, Pair<TreePath, V1>) -> Pair<TreePath, V2>
    ): TreeData<DWT, V2>
}
