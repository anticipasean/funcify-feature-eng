package funcify.feature.tree.spliterator

import funcify.feature.tree.path.PathSegment
import funcify.feature.tree.path.TreePath
import java.util.*
import java.util.function.Consumer
import java.util.stream.Stream

/**
 *
 * @author smccarron
 * @created 2023-04-19
 */
internal class TreeBreadthFirstSearchSpliterator<T>(
    private val rootPath: TreePath,
    private val root: T,
    private val traversalFunction: (T) -> Stream<Pair<PathSegment, T>>
) : Spliterator<Pair<TreePath, T>> {

    companion object {
        private const val TREE_SIZE_UNKNOWN = Long.MAX_VALUE
        private const val DEFAULT_CHARACTERISTICS: Int = Spliterator.ORDERED and Spliterator.NONNULL
    }

    private var expended: Boolean = false
    private val buffer: Deque<Pair<TreePath, T>> by lazy {
        LinkedList<Pair<TreePath, T>>().apply { add(rootPath to root) }
    }

    override fun tryAdvance(action: Consumer<in Pair<TreePath, T>>?): Boolean {
        if (action == null || expended) {
            return false
        }
        val pathTreePair: Pair<TreePath, T> = buffer.pollFirst()
        traversalFunction
            .invoke(pathTreePair.second)
            .map { p: Pair<PathSegment, T> ->
                pathTreePair.first.transform {
                    p.first.fold(
                        { idx: Int -> appendPathSegment(idx) },
                        { name: String -> appendPathSegment(name) }
                    )
                } to p.second
            }
            .forEachOrdered { p: Pair<TreePath, T> -> buffer.offerLast(p) }
        /*println(
            "buffer state: ${buffer.asSequence().joinToString(",\n") {(tp, v) -> "${tp}: ${v}"} }"
        )*/
        if (buffer.isEmpty()) {
            expended = true
        }
        action.accept(pathTreePair)
        return true
    }

    override fun trySplit(): Spliterator<Pair<TreePath, T>>? {
        return null
    }

    override fun estimateSize(): Long {
        return TREE_SIZE_UNKNOWN
    }

    override fun characteristics(): Int {
        return DEFAULT_CHARACTERISTICS
    }
}
