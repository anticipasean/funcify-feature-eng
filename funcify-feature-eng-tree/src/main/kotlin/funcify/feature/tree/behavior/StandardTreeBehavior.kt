package funcify.feature.tree.behavior

import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.firstOrNone
import arrow.core.getOrNone
import arrow.core.left
import arrow.core.none
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import funcify.feature.tree.ImmutableTree
import funcify.feature.tree.data.ArrayBranchData
import funcify.feature.tree.data.LeafData
import funcify.feature.tree.data.ObjectBranchData
import funcify.feature.tree.data.StandardArrayBranchData
import funcify.feature.tree.data.StandardLeafData
import funcify.feature.tree.data.StandardObjectBranchData
import funcify.feature.tree.data.StandardTreeData
import funcify.feature.tree.data.StandardTreeData.Companion.StandardTreeWT
import funcify.feature.tree.data.StandardTreeData.Companion.narrowed
import funcify.feature.tree.data.TreeData
import funcify.feature.tree.extensions.OptionExtensions.recurse
import funcify.feature.tree.path.IndexSegment
import funcify.feature.tree.path.NameSegment
import funcify.feature.tree.path.PathSegment
import funcify.feature.tree.path.TreePath
import funcify.feature.tree.spliterator.TreeBreadthFirstSearchSpliterator
import funcify.feature.tree.spliterator.TreeDepthFirstSearchSpliterator
import java.util.stream.IntStream
import java.util.stream.Stream
import java.util.stream.Stream.empty
import java.util.stream.StreamSupport
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList

/**
 *
 * @author smccarron
 * @created 2023-04-17
 */
internal interface StandardTreeBehavior : TreeBehavior<StandardTreeWT> {

    override fun <V, R> fold(
        container: TreeData<StandardTreeWT, V>,
        leafHandler: (LeafData<StandardTreeWT, V>) -> R,
        arrayTreeHandler: (ArrayBranchData<StandardTreeWT, V>) -> R,
        objectTreeHandler: (ObjectBranchData<StandardTreeWT, V>) -> R,
    ): R {
        return when (val st: StandardTreeData<V> = container.narrowed()) {
            is StandardLeafData<V> -> {
                leafHandler.invoke(st)
            }
            is StandardArrayBranchData<V> -> {
                arrayTreeHandler.invoke(st)
            }
            is StandardObjectBranchData<V> -> {
                objectTreeHandler.invoke(st)
            }
        }
    }

    override fun <V> value(container: TreeData<StandardTreeWT, V>): Option<V> {
        return when (val st: StandardTreeData<V> = container.narrowed()) {
            is StandardLeafData<V> -> {
                st.value.toOption()
            }
            is StandardArrayBranchData<V> -> {
                st.value.toOption()
            }
            is StandardObjectBranchData<V> -> {
                st.value.toOption()
            }
        }
    }

    override fun <V> contains(container: TreeData<StandardTreeWT, V>, path: TreePath): Boolean {
        return get(container, path).isDefined()
    }

    override fun <V> get(
        container: TreeData<StandardTreeWT, V>,
        path: TreePath
    ): Option<TreeData<StandardTreeWT, V>> {
        return (container.narrowed() to path.pathSegments.toPersistentList()).toOption().recurse {
            (st: StandardTreeData<V>, pl: PersistentList<PathSegment>) ->
            when {
                pl.isEmpty() -> {
                    st.right<StandardTreeData<V>>().some()
                }
                pl.firstOrNone()
                    .filter { ps: PathSegment ->
                        ps is NameSegment &&
                            st is StandardObjectBranchData<V> &&
                            st.children.containsKey(ps.name)
                    }
                    .isDefined() -> {
                    pl.firstOrNone()
                        .filterIsInstance<NameSegment>()
                        .flatMap { ns: NameSegment ->
                            (st as StandardObjectBranchData<V>).children.getOrNone(ns.name)
                        }
                        .map { cst -> (cst to pl.removeAt(0)).left() }
                }
                pl.firstOrNone()
                    .filter { ps: PathSegment ->
                        ps is IndexSegment &&
                            st is StandardArrayBranchData<V> &&
                            ps.index in st.children.indices
                    }
                    .isDefined() -> {
                    pl.firstOrNone()
                        .filterIsInstance<IndexSegment>()
                        .flatMap { idxs: IndexSegment ->
                            (st as StandardArrayBranchData<V>)
                                .children
                                .getOrNull(idxs.index)
                                .toOption()
                        }
                        .map { cst -> (cst to pl.removeAt(0)).left() }
                }
                else -> {
                    none()
                }
            }
        }
    }

    override fun <V, R> foldLeft(
        container: TreeData<StandardTreeWT, V>,
        startValue: R,
        accumulator: (R, V) -> R
    ): R {
        var accumulate: R = startValue
        for (p in depthFirstIterator(container)) {
            accumulate = accumulator(accumulate, p.second)
        }
        return accumulate
    }

    override fun <V, R> biFoldLeft(
        container: TreeData<StandardTreeWT, V>,
        startValue: R,
        accumulator: (R, TreePath, V) -> R
    ): R {
        var accumulate: R = startValue
        for (p in depthFirstIterator(container)) {
            accumulate = accumulator(accumulate, p.first, p.second)
        }
        return accumulate
    }

    override fun <V> depthFirstIterator(
        container: TreeData<StandardTreeWT, V>
    ): Iterator<Pair<TreePath, V>> {
        val traversalFunction = createTreeTraversalFunction<V>()
        return StreamSupport.stream(
                TreeDepthFirstSearchSpliterator<TreeData<StandardTreeWT, V>>(
                    container,
                    traversalFunction
                ),
                false
            )
            .flatMap { p: Pair<TreePath, TreeData<StandardTreeWT, V>> ->
                when (val v: V? = value(p.second).orNull()) {
                    null -> empty()
                    else -> Stream.of(p.first to v)
                }
            }
            .iterator()
    }

    fun <V> createTreeTraversalFunction():
        (TreeData<StandardTreeWT, V>) -> Stream<Pair<PathSegment, TreeData<StandardTreeWT, V>>> {
        return { td: TreeData<StandardTreeWT, V> ->
            when (val std: StandardTreeData<V> = td.narrowed()) {
                is StandardLeafData -> {
                    empty()
                }
                is StandardArrayBranchData -> {
                    IntStream.range(0, std.children.size).mapToObj { i: Int ->
                        IndexSegment(index = i) to std.children[i]
                    }
                }
                is StandardObjectBranchData -> {
                    std.children.entries.stream().map { (name: String, d: StandardTreeData<V>),
                        ->
                        NameSegment(name = name) to d
                    }
                }
            }
        }
    }

    override fun <V> breadthFirstIterator(
        container: TreeData<StandardTreeWT, V>
    ): Iterator<Pair<TreePath, V>> {
        val traversalFunction = createTreeTraversalFunction<V>()
        return StreamSupport.stream(
                TreeBreadthFirstSearchSpliterator<TreeData<StandardTreeWT, V>>(
                    container,
                    traversalFunction
                ),
                false
            )
            .flatMap { p: Pair<TreePath, TreeData<StandardTreeWT, V>> ->
                when (val v: V? = value(p.second).orNull()) {
                    null -> empty()
                    else -> Stream.of(p.first to v)
                }
            }
            .iterator()
    }

    override fun <V> descendent(
        container: TreeData<StandardTreeWT, V>,
        path: TreePath
    ): Option<TreeData<StandardTreeWT, V>> {
        TODO("Not yet implemented")
    }

    override fun <V> descendentsUnder(
        container: TreeData<StandardTreeWT, V>,
        path: TreePath
    ): Iterable<TreeData<StandardTreeWT, V>> {
        TODO("Not yet implemented")
    }

    override fun <V> children(
        container: TreeData<StandardTreeWT, V>
    ): Iterable<TreeData<StandardTreeWT, V>> {
        TODO("Not yet implemented")
    }

    override fun <V, V1> map(
        container: TreeData<StandardTreeWT, V>,
        function: (V) -> V1
    ): TreeData<StandardTreeWT, V1> {
        TODO("Not yet implemented")
    }

    override fun <V, V1> bimap(
        container: TreeData<StandardTreeWT, V>,
        function: (TreePath, V) -> Pair<TreePath, V1>
    ): TreeData<StandardTreeWT, V1> {
        TODO("Not yet implemented")
    }

    override fun <V, V1> bimap(
        container: TreeData<StandardTreeWT, V>,
        pathMapper: (TreePath) -> TreePath,
        valueMapper: (V) -> V1,
    ): TreeData<StandardTreeWT, V1> {
        TODO("Not yet implemented")
    }

    override fun <V> filter(
        container: TreeData<StandardTreeWT, V>,
        condition: (V) -> Boolean
    ): TreeData<StandardTreeWT, V> {
        TODO("Not yet implemented")
    }

    override fun <V, V1> flatMap(
        container: TreeData<StandardTreeWT, V>,
        function: (V) -> ImmutableTree<V1>
    ): TreeData<StandardTreeWT, V1> {
        TODO("Not yet implemented")
    }

    override fun <V, V1> biFlatMap(
        container: TreeData<StandardTreeWT, V>,
        function: (TreePath, V) -> ImmutableTree<V1>,
    ): TreeData<StandardTreeWT, V1> {
        TODO("Not yet implemented")
    }

    override fun <V, V1, V2> zip(
        container: TreeData<StandardTreeWT, V>,
        other: ImmutableTree<V1>,
        function: (V, V1) -> V2,
    ): TreeData<StandardTreeWT, V2> {
        TODO("Not yet implemented")
    }
}
