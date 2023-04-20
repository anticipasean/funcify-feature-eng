package funcify.feature.tree.design

import arrow.core.Option
import funcify.feature.tree.ImmutableTree
import funcify.feature.tree.PersistentTree
import funcify.feature.tree.behavior.TreeBehavior
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
internal interface PersistentTreeDesign<DWT, V> : PersistentTree<V> {

    val behavior: TreeBehavior<DWT>

    val data: TreeData<DWT, V>

    fun <V> leaf(data: LeafData<DWT, V>): LeafDesign<DWT, V>

    fun <V> arrayBranch(data: ArrayBranchData<DWT, V>): ArrayBranchDesign<DWT, V>

    fun <V> objectBranch(data: ObjectBranchData<DWT, V>): ObjectBranchDesign<DWT, V>

    override fun value(): Option<V> {
        return this.behavior.value(container = this.data)
    }

    override fun contains(path: TreePath): Boolean {
        return this.behavior.contains(container = this.data, path = path)
    }

    override fun get(path: TreePath): Option<PersistentTree<V>> {
        return this.behavior.get(container = this.data, path = path).mapNotNull {
            d: TreeData<DWT, V> ->
            when (d) {
                is LeafData<*, *> -> leaf(d as LeafData<DWT, V>)
                is ArrayBranchData<*, *> -> arrayBranch(d as ArrayBranchData<DWT, V>)
                is ObjectBranchData<*, *> -> objectBranch(d as ObjectBranchData<DWT, V>)
                else -> null
            }
        }
    }

    override fun <R> foldLeft(startValue: R, accumulator: (R, V) -> R): R {
        return this.behavior.foldLeft(
            container = this.data,
            startValue = startValue,
            accumulator = accumulator
        )
    }

    override fun <R> biFoldLeft(startValue: R, accumulator: (R, TreePath, V) -> R): R {
        return this.behavior.biFoldLeft(
            container = this.data,
            startValue = startValue,
            accumulator = accumulator
        )
    }

    override fun depthFirstIterator(): Iterator<Pair<TreePath, V>> {
        return this.behavior.depthFirstIterator(container = this.data)
    }

    override fun breadthFirstIterator(): Iterator<Pair<TreePath, V>> {
        return this.behavior.breadthFirstIterator(container = this.data)
    }

    override fun descendentsUnder(path: TreePath): Iterable<PersistentTree<V>> {
        return this.behavior
            .descendentsUnder(container = this.data, path = path)
            .asSequence()
            .mapNotNull { d: TreeData<DWT, V> ->
                when (d) {
                    is LeafData<*, *> -> leaf(d as LeafData<DWT, V>)
                    is ArrayBranchData<*, *> -> arrayBranch(d as ArrayBranchData<DWT, V>)
                    is ObjectBranchData<*, *> -> objectBranch(d as ObjectBranchData<DWT, V>)
                    else -> null
                }
            }
            .asIterable()
    }

    override fun children(): Iterable<PersistentTree<V>> {
        return this.behavior
            .children(container = this.data)
            .asSequence()
            .mapNotNull { d: TreeData<DWT, V> ->
                when (d) {
                    is LeafData<*, *> -> leaf(d as LeafData<DWT, V>)
                    is ArrayBranchData<*, *> -> arrayBranch(d as ArrayBranchData<DWT, V>)
                    is ObjectBranchData<*, *> -> objectBranch(d as ObjectBranchData<DWT, V>)
                    else -> null
                }
            }
            .asIterable()
    }

    override fun <V1> map(function: (V) -> V1): PersistentTree<V1> {
        return when (
            val d: TreeData<DWT, V1> = this.behavior.map(container = this.data, function = function)
        ) {
            is LeafData<*, *> -> leaf(d as LeafData<DWT, V1>)
            is ArrayBranchData<*, *> -> arrayBranch(d as ArrayBranchData<DWT, V1>)
            is ObjectBranchData<*, *> -> objectBranch(d as ObjectBranchData<DWT, V1>)
            else -> {
                throw IllegalStateException(
                    "tree_data instance [ type: ${d::class.qualifiedName} ] not supported"
                )
            }
        }
    }

    override fun <V1> bimap(function: (TreePath, V) -> Pair<TreePath, V1>): PersistentTree<V1> {
        return when (
            val d: TreeData<DWT, V1> =
                this.behavior.bimap(container = this.data, function = function)
        ) {
            is LeafData<*, *> -> leaf(d as LeafData<DWT, V1>)
            is ArrayBranchData<*, *> -> arrayBranch(d as ArrayBranchData<DWT, V1>)
            is ObjectBranchData<*, *> -> objectBranch(d as ObjectBranchData<DWT, V1>)
            else -> {
                throw IllegalStateException(
                    "tree_data instance [ type: ${d::class.qualifiedName} ] not supported"
                )
            }
        }
    }

    override fun <V1> bimap(
        pathMapper: (TreePath) -> TreePath,
        valueMapper: (V) -> V1
    ): PersistentTree<V1> {
        return when (
            val d: TreeData<DWT, V1> =
                this.behavior.bimap(
                    container = this.data,
                    pathMapper = pathMapper,
                    valueMapper = valueMapper
                )
        ) {
            is LeafData<*, *> -> leaf(d as LeafData<DWT, V1>)
            is ArrayBranchData<*, *> -> arrayBranch(d as ArrayBranchData<DWT, V1>)
            is ObjectBranchData<*, *> -> objectBranch(d as ObjectBranchData<DWT, V1>)
            else -> {
                throw IllegalStateException(
                    "tree_data instance [ type: ${d::class.qualifiedName} ] not supported"
                )
            }
        }
    }

    override fun filter(condition: (V) -> Boolean): PersistentTree<V> {
        return when (
            val d: TreeData<DWT, V> =
                this.behavior.filter(container = this.data, condition = condition)
        ) {
            is LeafData<*, *> -> leaf(d as LeafData<DWT, V>)
            is ArrayBranchData<*, *> -> arrayBranch(d as ArrayBranchData<DWT, V>)
            is ObjectBranchData<*, *> -> objectBranch(d as ObjectBranchData<DWT, V>)
            else -> {
                throw IllegalStateException(
                    "tree_data instance [ type: ${d::class.qualifiedName} ] not supported"
                )
            }
        }
    }

    override fun <V1> flatMap(function: (V) -> ImmutableTree<V1>): PersistentTree<V1> {
        return when (
            val d: TreeData<DWT, V1> =
                this.behavior.flatMap(container = this.data, function = function)
        ) {
            is LeafData<*, *> -> leaf(d as LeafData<DWT, V1>)
            is ArrayBranchData<*, *> -> arrayBranch(d as ArrayBranchData<DWT, V1>)
            is ObjectBranchData<*, *> -> objectBranch(d as ObjectBranchData<DWT, V1>)
            else -> {
                throw IllegalStateException(
                    "tree_data instance [ type: ${d::class.qualifiedName} ] not supported"
                )
            }
        }
    }

    override fun <V1> biFlatMap(function: (TreePath, V) -> ImmutableTree<V1>): PersistentTree<V1> {
        return when (
            val d: TreeData<DWT, V1> =
                this.behavior.biFlatMap(container = this.data, function = function)
        ) {
            is LeafData<*, *> -> leaf(d as LeafData<DWT, V1>)
            is ArrayBranchData<*, *> -> arrayBranch(d as ArrayBranchData<DWT, V1>)
            is ObjectBranchData<*, *> -> objectBranch(d as ObjectBranchData<DWT, V1>)
            else -> {
                throw IllegalStateException(
                    "tree_data instance [ type: ${d::class.qualifiedName} ] not supported"
                )
            }
        }
    }

    override fun <V1, V2> zip(
        other: ImmutableTree<V1>,
        function: (V, V1) -> V2
    ): PersistentTree<V2> {
        return when (
            val d: TreeData<DWT, V2> =
                this.behavior.zip(container = this.data, other = other, function = function)
        ) {
            is LeafData<*, *> -> leaf(d as LeafData<DWT, V2>)
            is ArrayBranchData<*, *> -> arrayBranch(d as ArrayBranchData<DWT, V2>)
            is ObjectBranchData<*, *> -> objectBranch(d as ObjectBranchData<DWT, V2>)
            else -> {
                throw IllegalStateException(
                    "tree_data instance [ type: ${d::class.qualifiedName} ] not supported"
                )
            }
        }
    }
}
