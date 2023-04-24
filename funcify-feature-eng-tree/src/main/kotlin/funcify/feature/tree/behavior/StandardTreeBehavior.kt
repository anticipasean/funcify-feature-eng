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
import java.util.*
import java.util.stream.IntStream
import java.util.stream.Stream
import java.util.stream.Stream.empty
import java.util.stream.StreamSupport
import kotlin.streams.asStream
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

    // TODO: Consider memoizing resolved paths since structure is immutable so subsequent
    // calculations should yield same result for same instance
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
                    TreePath.getRootPath(),
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
                    std.children.entries.stream().map { (n: String, d: StandardTreeData<V>) ->
                        NameSegment(name = n) to d
                    }
                }
            }
        }
    }

    override fun <V> breadthFirstIterator(
        container: TreeData<StandardTreeWT, V>
    ): Iterator<Pair<TreePath, V>> {
        return StreamSupport.stream(
                TreeBreadthFirstSearchSpliterator<TreeData<StandardTreeWT, V>>(
                    TreePath.getRootPath(),
                    container,
                    createTreeTraversalFunction()
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

    override fun <V> descendentsUnder(
        container: TreeData<StandardTreeWT, V>,
        path: TreePath
    ): Iterable<TreeData<StandardTreeWT, V>> {
        return when (
            val td: TreeData<StandardTreeWT, V>? = get(container = container, path = path).orNull()
        ) {
            null -> {
                emptyList<TreeData<StandardTreeWT, V>>()
            }
            else -> {
                Iterable {
                    StreamSupport.stream(
                            TreeBreadthFirstSearchSpliterator<TreeData<StandardTreeWT, V>>(
                                path,
                                td,
                                createTreeTraversalFunction<V>()
                            ),
                            false
                        )
                        .map { (_, desc: TreeData<StandardTreeWT, V>) -> desc }
                        .iterator()
                }
            }
        }
    }

    override fun <V> children(
        container: TreeData<StandardTreeWT, V>
    ): Iterable<TreeData<StandardTreeWT, V>> {
        return when (val td: StandardTreeData<V> = container.narrowed()) {
            is StandardLeafData -> {
                emptyList()
            }
            is StandardArrayBranchData -> {
                td.children
            }
            is StandardObjectBranchData -> {
                td.children.values
            }
        }
    }

    override fun <V, V1> map(
        container: TreeData<StandardTreeWT, V>,
        function: (V) -> V1
    ): TreeData<StandardTreeWT, V1> {
        return when (val td: StandardTreeData<V> = container.narrowed()) {
            is StandardLeafData -> {
                StandardTreeData.getRoot<V1>()
            }
            else -> {
                StreamSupport.stream(
                        TreeBreadthFirstSearchSpliterator(
                            TreePath.getRootPath(),
                            td,
                            createTreeTraversalFunction<V>()
                        ),
                        false
                    )
                    .flatMap { (tp: TreePath, ctd: TreeData<StandardTreeWT, V>) ->
                        when (val v: V? = value(ctd).orNull()) {
                            null -> {
                                empty()
                            }
                            else -> {
                                Stream.of(tp to function.invoke(v))
                            }
                        }
                    }
                    .let { stream: Stream<out Pair<TreePath, V1>> ->
                        StreamToStandardTreeDataMapper.createStandardTreeDataFromStream<V1>(
                            stream = stream
                        )
                    }
            }
        }
    }

    override fun <V, V1> bimap(
        container: TreeData<StandardTreeWT, V>,
        function: (TreePath, V) -> Pair<TreePath, V1>
    ): TreeData<StandardTreeWT, V1> {
        return when (val td: StandardTreeData<V> = container.narrowed()) {
            is StandardLeafData -> {
                StandardTreeData.getRoot<V1>()
            }
            else -> {
                StreamSupport.stream(
                        TreeBreadthFirstSearchSpliterator(
                            TreePath.getRootPath(),
                            td,
                            createTreeTraversalFunction<V>()
                        ),
                        false
                    )
                    .flatMap { (tp: TreePath, ctd: TreeData<StandardTreeWT, V>) ->
                        when (val v: V? = value(ctd).orNull()) {
                            null -> {
                                empty()
                            }
                            else -> {
                                Stream.of(function.invoke(tp, v))
                            }
                        }
                    }
                    .let { stream: Stream<out Pair<TreePath, V1>> ->
                        StreamToStandardTreeDataMapper.createStandardTreeDataFromStream<V1>(
                            stream = stream
                        )
                    }
            }
        }
    }

    override fun <V, V1> bimap(
        container: TreeData<StandardTreeWT, V>,
        pathMapper: (TreePath) -> TreePath,
        valueMapper: (V) -> V1,
    ): TreeData<StandardTreeWT, V1> {
        return when (val td: StandardTreeData<V> = container.narrowed()) {
            is StandardLeafData -> {
                StandardTreeData.getRoot<V1>()
            }
            else -> {
                StreamSupport.stream(
                        TreeBreadthFirstSearchSpliterator(
                            TreePath.getRootPath(),
                            td,
                            createTreeTraversalFunction<V>()
                        ),
                        false
                    )
                    .flatMap { (tp: TreePath, ctd: TreeData<StandardTreeWT, V>) ->
                        when (val v: V? = value(ctd).orNull()) {
                            null -> {
                                empty()
                            }
                            else -> {
                                Stream.of(pathMapper.invoke(tp) to valueMapper.invoke(v))
                            }
                        }
                    }
                    .let { stream: Stream<out Pair<TreePath, V1>> ->
                        StreamToStandardTreeDataMapper.createStandardTreeDataFromStream<V1>(
                            stream = stream
                        )
                    }
            }
        }
    }

    override fun <V> filter(
        container: TreeData<StandardTreeWT, V>,
        condition: (V) -> Boolean
    ): TreeData<StandardTreeWT, V> {
        return biFilter(container) { _: TreePath, v: V -> condition.invoke(v) }
    }

    override fun <V> biFilter(
        container: TreeData<StandardTreeWT, V>,
        condition: (TreePath, V) -> Boolean
    ): TreeData<StandardTreeWT, V> {
        return when (val td: StandardTreeData<V> = container.narrowed()) {
            is StandardLeafData -> {
                StandardTreeData.getRoot<V>()
            }
            else -> {
                StreamSupport.stream(
                        TreeBreadthFirstSearchSpliterator(
                            TreePath.getRootPath(),
                            td,
                            createTreeTraversalFunction<V>()
                        ),
                        false
                    )
                    .flatMap { (tp: TreePath, ctd: TreeData<StandardTreeWT, V>) ->
                        when (val v: V? = value(ctd).orNull()) {
                            null -> {
                                empty()
                            }
                            else -> {
                                if (condition.invoke(tp, v)) {
                                    Stream.of(tp to v)
                                } else {
                                    empty()
                                }
                            }
                        }
                    }
                    .let { stream: Stream<out Pair<TreePath, V>> ->
                        StreamToStandardTreeDataMapper.createStandardTreeDataFromStream<V>(
                            stream = stream
                        )
                    }
            }
        }
    }

    override fun <V, V1> flatMap(
        container: TreeData<StandardTreeWT, V>,
        function: (V) -> ImmutableTree<V1>
    ): TreeData<StandardTreeWT, V1> {
        return biFlatMap(container) { _: TreePath, v: V -> function(v) }
    }

    override fun <V, V1> biFlatMap(
        container: TreeData<StandardTreeWT, V>,
        function: (TreePath, V) -> ImmutableTree<V1>,
    ): TreeData<StandardTreeWT, V1> {
        return when (val td: StandardTreeData<V> = container.narrowed()) {
            is StandardLeafData -> {
                StandardTreeData.getRoot<V1>()
            }
            else -> {
                StreamSupport.stream(
                        TreeBreadthFirstSearchSpliterator(
                            TreePath.getRootPath(),
                            td,
                            createTreeTraversalFunction<V>()
                        ),
                        false
                    )
                    .flatMap { (tp: TreePath, ctd: TreeData<StandardTreeWT, V>) ->
                        when (val v: V? = value(ctd).orNull()) {
                            null -> {
                                empty()
                            }
                            else -> {
                                StreamSupport.stream(
                                    Spliterators.spliteratorUnknownSize(
                                        function.invoke(tp, v).breadthFirstIterator(),
                                        0
                                    ),
                                    false
                                )
                            }
                        }
                    }
                    .let { stream: Stream<out Pair<TreePath, V1>> ->
                        StreamToStandardTreeDataMapper.createStandardTreeDataFromStream<V1>(
                            stream = stream
                        )
                    }
            }
        }
    }

    override fun <V, V1, V2> zip(
        container: TreeData<StandardTreeWT, V>,
        other: ImmutableTree<V1>,
        function: (V, V1) -> V2,
    ): TreeData<StandardTreeWT, V2> {
        return biZip(container, other) { p1: Pair<TreePath, V>, p2: Pair<TreePath, V1> ->
            p1.first to function.invoke(p1.second, p2.second)
        }
    }

    override fun <V, V1, V2> biZip(
        container: TreeData<StandardTreeWT, V>,
        other: ImmutableTree<V1>,
        function: (Pair<TreePath, V>, Pair<TreePath, V1>) -> Pair<TreePath, V2>,
    ): TreeData<StandardTreeWT, V2> {
        return when (val td: StandardTreeData<V> = container.narrowed()) {
            is StandardLeafData -> {
                StandardTreeData.getRoot<V2>()
            }
            else -> {
                Spliterators.iterator(
                        TreeBreadthFirstSearchSpliterator(
                            TreePath.getRootPath(),
                            td,
                            createTreeTraversalFunction<V>()
                        )
                    )
                    .asSequence()
                    .zip(other.breadthFirstIterator().asSequence()) { p1, p2 ->
                        when (val v: V? = value(p1.second).orNull()) {
                            null -> {
                                emptySequence()
                            }
                            else -> {
                                sequenceOf(function.invoke(p1.first to v, p2))
                            }
                        }
                    }
                    .flatMap { s: Sequence<Pair<TreePath, V2>> -> s }
                    .asStream()
                    .let { stream: Stream<Pair<TreePath, V2>> ->
                        StreamToStandardTreeDataMapper.createStandardTreeDataFromStream<V2>(
                            stream = stream
                        )
                    }
            }
        }
    }
}
