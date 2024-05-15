package funcify.feature.tools.spliterator

import arrow.core.Either
import arrow.core.left
import java.util.*
import java.util.function.Consumer

/**
 * @author smccarron
 * @created 2023-10-03
 */
internal class DepthFirstEitherRecursiveSpliterator<L, R>(
    private val initialLeftValue: L,
    private val traversalFunction: (L) -> Iterator<Either<L, R>>
) : Spliterator<R> {

    companion object {
        private const val DEFAULT_CHARACTERISTICS: Int =
            Spliterator.NONNULL or Spliterator.IMMUTABLE
        private const val SIZE_UNKNOWN: Long = Long.MAX_VALUE
    }

    // TODO: Could add deque size limit in case someone passes non-terminating traversal
    private val deque: Deque<Either<L, R>> =
        LinkedList<Either<L, R>>().apply { addFirst(initialLeftValue.left()) }

    override fun tryAdvance(action: Consumer<in R>?): Boolean {
        return when {
            action == null -> {
                false
            }
            else -> {
                when (val nextRightValue: R? = traverseUntilNextRightValue()) {
                    null -> {
                        false
                    }
                    else -> {
                        action.accept(nextRightValue)
                        true
                    }
                }
            }
        }
    }

    private fun traverseUntilNextRightValue(): R? {
        try {
            val buffer: Deque<Either<L, R>> = LinkedList()
            while (deque.isNotEmpty()) {
                when (val nextResult: Either<L, R> = deque.pollFirst()) {
                    is Either.Right<R> -> {
                        return nextResult.value
                    }
                    is Either.Left<L> -> {
                        for (e: Either<L, R> in traversalFunction.invoke(nextResult.value)) {
                            buffer.offerFirst(e)
                        }
                        for (e: Either<L, R> in buffer) {
                            deque.offerFirst(e)
                        }
                        buffer.clear()
                    }
                }
            }
            return null
        } catch (e: OutOfMemoryError) {
            throw Error(
                "out-of-memory error occurred likely due to non-terminating recursive operation",
                e
            )
        }
    }

    override fun trySplit(): Spliterator<R>? {
        // Size unknown so can't split
        return null
    }

    override fun estimateSize(): Long {
        return SIZE_UNKNOWN
    }

    override fun characteristics(): Int {
        return DEFAULT_CHARACTERISTICS
    }
}
