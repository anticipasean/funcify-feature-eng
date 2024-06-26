package funcify.feature.tree.behavior

import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.firstOrNone
import arrow.core.left
import arrow.core.none
import arrow.core.right
import arrow.core.some
import arrow.core.toOption
import funcify.feature.tree.ImmutableTree
import funcify.feature.tree.data.*
import funcify.feature.tree.data.StandardTreeData.Companion.StandardTreeWT
import funcify.feature.tree.data.StandardTreeData.Companion.narrowed
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
import kotlin.streams.asSequence
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList

/**
 *
 * @author smccarron
 * @created 2023-04-17
 */
internal interface StandardTreeBehavior : TreeBehavior<StandardTreeWT> {

    override fun <V> fromSequence(
        sequence: Sequence<Pair<TreePath, V>>
    ): TreeData<StandardTreeWT, V> {
        return SequenceToStandardTreeDataMapper.createStandardTreeDataFromSequence<V>(sequence)
    }

    override fun <V> value(container: TreeData<StandardTreeWT, V>): Option<V> {
        return when (val st: StandardTreeData<V> = container.narrowed()) {
            is StandardEmptyTreeData<V> -> {
                none<V>()
            }
            is StandardNonEmptyTreeData<V> -> {
                when (st) {
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
        return when (val td: StandardTreeData<V> = container.narrowed()) {
            is StandardEmptyTreeData<V> -> {
                none<TreeData<StandardTreeWT, V>>()
            }
            is StandardNonEmptyTreeData<V> -> {
                (td to path.pathSegments.toPersistentList()).toOption().recurse {
                    (st: StandardNonEmptyTreeData<V>, pl: PersistentList<PathSegment>) ->
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
                                .mapNotNull { ns: NameSegment ->
                                    (st as StandardObjectBranchData<V>).children[ns.name]
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
                                .mapNotNull { idxs: IndexSegment ->
                                    (st as StandardArrayBranchData<V>)
                                        .children
                                        .getOrNull(idxs.index)
                                }
                                .map { cst -> (cst to pl.removeAt(0)).left() }
                        }
                        else -> {
                            none()
                        }
                    }
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
        for (p: Pair<TreePath, V> in depthFirstIterator(container)) {
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
        for (p: Pair<TreePath, V> in depthFirstIterator(container)) {
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
        (TreeData<StandardTreeWT, V>) -> Stream<
                out Pair<PathSegment, TreeData<StandardTreeWT, V>>> {
        return { td: TreeData<StandardTreeWT, V> ->
            when (val std: StandardTreeData<V> = td.narrowed()) {
                is StandardEmptyTreeData<V> -> {
                    empty()
                }
                is StandardNonEmptyTreeData<V> -> {
                    when (std) {
                        is StandardLeafData<V> -> {
                            empty()
                        }
                        is StandardArrayBranchData<V> -> {
                            IntStream.range(0, std.children.size).mapToObj { i: Int ->
                                IndexSegment(index = i) to std.children[i]
                            }
                        }
                        is StandardObjectBranchData<V> -> {
                            std.children.entries.stream().map { (n: String, d: StandardTreeData<V>)
                                ->
                                NameSegment(name = n) to d
                            }
                        }
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

    override fun <V> size(container: TreeData<StandardTreeWT, V>): Int {
        return when (val td: StandardTreeData<V> = container.narrowed()) {
            is StandardEmptyTreeData<V> -> {
                0
            }
            is StandardNonEmptyTreeData<V> -> {
                when (td) {
                    is StandardLeafData<V> -> {
                        1
                    }
                    is StandardArrayBranchData<V> -> {
                        1 + td.subNodeCount
                    }
                    is StandardObjectBranchData<V> -> {
                        1 + td.subNodeCount
                    }
                }
            }
        }
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
            is StandardEmptyTreeData<V> -> {
                emptyList()
            }
            is StandardNonEmptyTreeData<V> -> {
                when (td) {
                    is StandardLeafData<V> -> {
                        emptyList()
                    }
                    is StandardArrayBranchData<V> -> {
                        td.children
                    }
                    is StandardObjectBranchData<V> -> {
                        td.children.values
                    }
                }
            }
        }
    }

    override fun <V> levels(
        container: TreeData<StandardTreeWT, V>
    ): Iterable<Pair<Int, Iterable<Pair<TreePath, V>>>> {
        return StreamSupport.stream(
                TreeDepthFirstSearchSpliterator<TreeData<StandardTreeWT, V>>(
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
            .reduce(
                persistentMapOf<Int, PersistentList<Pair<TreePath, V>>>(),
                { pm: PersistentMap<Int, PersistentList<Pair<TreePath, V>>>, p: Pair<TreePath, V> ->
                    val level: Int = p.first.pathSegments.size
                    pm.put(level, pm.getOrElse(level) { persistentListOf() }.add(p))
                },
                PersistentMap<Int, PersistentList<Pair<TreePath, V>>>::putAll
            )
            .asSequence()
            .map(Map.Entry<Int, PersistentList<Pair<TreePath, V>>>::toPair)
            .asIterable()
    }

    override fun <V, V1> map(
        container: TreeData<StandardTreeWT, V>,
        function: (V) -> V1
    ): TreeData<StandardTreeWT, V1> {
        return bimap(container) { tp: TreePath, v: V -> tp to function.invoke(v) }
    }

    override fun <V, V1> bimap(
        container: TreeData<StandardTreeWT, V>,
        function: (TreePath, V) -> Pair<TreePath, V1>
    ): TreeData<StandardTreeWT, V1> {
        return when (val td: StandardTreeData<V> = container.narrowed()) {
            is StandardEmptyTreeData<V> -> {
                StandardEmptyTreeData.getInstance<V1>()
            }
            is StandardNonEmptyTreeData<V> -> {
                when (td) {
                    is StandardLeafData<V> -> {
                        when (val v: V? = td.value) {
                            null -> {
                                StandardLeafData<V1>(null)
                            }
                            else -> {
                                StandardLeafData<V1>(
                                    function.invoke(TreePath.getRootPath(), v).second
                                )
                            }
                        }
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
                            .asSequence()
                            .let { sequence: Sequence<Pair<TreePath, V1>> ->
                                fromSequence(sequence)
                            }
                    }
                }
            }
        }
    }

    override fun <V, V1> bimap(
        container: TreeData<StandardTreeWT, V>,
        pathMapper: (TreePath) -> TreePath,
        valueMapper: (V) -> V1,
    ): TreeData<StandardTreeWT, V1> {
        return bimap(container) { tp: TreePath, v: V ->
            pathMapper.invoke(tp) to valueMapper.invoke(v)
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
            is StandardEmptyTreeData<V> -> {
                StandardEmptyTreeData.getInstance<V>()
            }
            is StandardNonEmptyTreeData<V> -> {
                when (td) {
                    is StandardLeafData<V> -> {
                        when (val v: V? = td.value) {
                            null -> {
                                StandardEmptyTreeData.getInstance<V>()
                            }
                            else -> {
                                if (condition.invoke(TreePath.getRootPath(), v)) {
                                    td
                                } else {
                                    StandardEmptyTreeData.getInstance<V>()
                                }
                            }
                        }
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
                            .asSequence()
                            .let { sequence: Sequence<Pair<TreePath, V>> -> fromSequence(sequence) }
                    }
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
            is StandardEmptyTreeData<V> -> {
                StandardEmptyTreeData.getInstance<V1>()
            }
            is StandardNonEmptyTreeData<V> -> {
                when (td) {
                    is StandardLeafData<V> -> {
                        when (val v: V? = td.value) {
                            null -> {
                                StandardEmptyTreeData.getInstance<V1>()
                            }
                            else -> {
                                StreamSupport.stream(
                                        Spliterators.spliteratorUnknownSize(
                                            function
                                                .invoke(TreePath.getRootPath(), v)
                                                .breadthFirstIterator(),
                                            0
                                        ),
                                        false
                                    )
                                    .asSequence()
                                    .let { sequence: Sequence<Pair<TreePath, V1>> ->
                                        fromSequence(sequence)
                                    }
                            }
                        }
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
                            .asSequence()
                            .let { sequence: Sequence<Pair<TreePath, V1>> ->
                                fromSequence(sequence)
                            }
                    }
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
            is StandardEmptyTreeData<V> -> {
                StandardEmptyTreeData.getInstance<V2>()
            }
            is StandardNonEmptyTreeData<V> -> {
                when (td) {
                    is StandardLeafData<V> -> {
                        when (val v: V? = td.value) {
                            null -> {
                                StandardEmptyTreeData.getInstance<V2>()
                            }
                            else -> {
                                val iter = other.breadthFirstIterator()
                                if (iter.hasNext()) {
                                    fromSequence(
                                        sequenceOf(
                                            function.invoke(
                                                TreePath.getRootPath() to v,
                                                iter.next()
                                            )
                                        )
                                    )
                                } else {
                                    StandardEmptyTreeData.getInstance<V2>()
                                }
                            }
                        }
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
                            .let { sequence: Sequence<Pair<TreePath, V2>> ->
                                fromSequence(sequence)
                            }
                    }
                }
            }
        }
    }
}
