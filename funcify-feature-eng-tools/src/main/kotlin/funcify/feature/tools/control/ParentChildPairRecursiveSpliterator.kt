package funcify.feature.tools.control

import java.util.Deque
import java.util.LinkedList
import java.util.Spliterator
import java.util.function.Consumer
import java.util.stream.Stream


/**
 *
 * @author smccarron
 * @created 4/9/22
 */
class ParentChildPairRecursiveSpliterator<T>(private val rootValue: T,
                                             private val traversalFunction: (T) -> Stream<T>) : Spliterator<Pair<T, T>> {

    companion object {
        private const val DEFAULT_CHARACTERISTICS: Int = Spliterator.NONNULL and Spliterator.IMMUTABLE
    }

    private val queue: Deque<Pair<T, T>> = LinkedList()

    private var expended: Boolean = false

    override fun tryAdvance(action: Consumer<in Pair<T, T>>?): Boolean {
        if (action == null || expended) {
            return false
        }
        if (queue.isNotEmpty()) {
            val (parent, child) = queue.pollFirst()
            traversalFunction.invoke(child)
                    .map { grandChild: T -> child to grandChild }
                    .forEach { pair ->
                        queue.offerLast(pair)
                    }
            if (queue.isEmpty()) {
                expended = true
            }
            action.accept(parent to child)
            return true
        } else {
            traversalFunction.invoke(rootValue)
                    .map { child: T -> rootValue to child }
                    .forEach { pair ->
                        queue.offerLast(pair)
                    }
            if (queue.isEmpty()) {
                expended = true
                return false
            } else {
                val (root, child) = queue.pollFirst()
                traversalFunction.invoke(child)
                        .map { grandChild: T -> child to grandChild }
                        .forEach { pair ->
                            queue.offerLast(pair)
                        }
                if (queue.isEmpty()) {
                    expended = true
                }
                action.accept(root to child)
                return true
            }
        }
    }

    override fun trySplit(): Spliterator<Pair<T, T>>? { // cannot be sized in advance so cannot be split
        return null
    }

    override fun estimateSize(): Long { // cannot be sized in advance
        return Long.MAX_VALUE
    }

    override fun characteristics(): Int {
        return DEFAULT_CHARACTERISTICS
    }
}