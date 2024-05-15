package funcify.feature.tools.control

import java.util.*
import java.util.function.Consumer
import java.util.stream.Stream

/**
 * @author smccarron
 * @created 4/9/22
 */
internal class ParentChildPairRecursiveSpliterator<T>(
    private val rootValue: T,
    private val traversalFunction: (T) -> Stream<out T>
) : ParentChildPairRelationshipSpliterator<T, T> {

    companion object {
        private const val DEFAULT_CHARACTERISTICS: Int =
            Spliterator.NONNULL or Spliterator.IMMUTABLE
    }

    private val queue: Deque<Pair<T, T>> = LinkedList()

    private var expended: Boolean = false

    override fun tryAdvance(action: Consumer<in Pair<T, T>>?): Boolean {
        if (action == null || expended) {
            return false
        }
        if (queue.isNotEmpty()) {
            val (parent, child) = queue.pollFirst()
            traversalFunction
                .invoke(child)
                .map { grandChild: T -> child to grandChild }
                .forEach { pair -> queue.offerLast(pair) }
            if (queue.isEmpty()) {
                expended = true
            }
            action.accept(parent to child)
            return true
        } else {
            traversalFunction
                .invoke(rootValue)
                .map { child: T -> rootValue to child }
                .forEach { pair -> queue.offerLast(pair) }
            return if (queue.isEmpty()) {
                expended = true
                false
            } else {
                val (root, child) = queue.pollFirst()
                traversalFunction
                    .invoke(child)
                    .map { grandChild: T -> child to grandChild }
                    .forEach { pair -> queue.offerLast(pair) }
                if (queue.isEmpty()) {
                    expended = true
                }
                action.accept(root to child)
                true
            }
        }
    }

    override fun trySplit():
        Spliterator<Pair<T, T>>? { // cannot be sized in advance so cannot be split
        return null
    }

    override fun estimateSize(): Long { // cannot be sized in advance
        return Long.MAX_VALUE
    }

    override fun characteristics(): Int {
        return DEFAULT_CHARACTERISTICS
    }
}
