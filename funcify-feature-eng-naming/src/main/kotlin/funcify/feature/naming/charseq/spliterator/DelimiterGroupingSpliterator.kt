package funcify.feature.naming.charseq.spliterator

import funcify.feature.naming.charseq.group.DelimitedCharGroup
import funcify.feature.naming.charseq.group.DelimiterCharGroup
import java.util.Deque
import java.util.LinkedList
import java.util.Spliterator
import java.util.Spliterator.IMMUTABLE
import java.util.Spliterator.NONNULL
import java.util.function.Consumer


/**
 *
 * @author smccarron
 * @created 3/14/22
 */
internal class DelimiterGroupingSpliterator(private val sourceSpliterator: Spliterator<Char>,
                                            private val delimiterFilter: (Char) -> Boolean,
                                            private val characteristicsBitSet: Int = DEFAULT_CHARACTERISTICS,
                                            private var delimiterQueue: Deque<Char> = LinkedList(),
                                            private var delimitedQueue: Deque<Char> = LinkedList()) : Spliterator<CharSequence> {

    companion object {
        // Not ordered (no clear comparison of charcontextgroups) or sized (unknown how many charcontextgroups will be extracted from source)
        private const val DEFAULT_CHARACTERISTICS: Int = IMMUTABLE and NONNULL
        private const val DEFAULT_SIZE_IF_UNKNOWN: Long = Long.MAX_VALUE
    }

    override fun tryAdvance(action: Consumer<in CharSequence>?): Boolean {
        if (action == null) {
            return false
        }
        val attemptToAdvance = booleanArrayOf(true)
        /**
         * Case 1: A delimiter char group (sequence of delimiter characters) exists
         * from a previous advance on the source spliterator terminating
         * at the start of a delimited group
         * => Consume remaining delimiter group, empty current and replace with new
         */
        if (delimiterQueue.isNotEmpty()) {
            while (attemptToAdvance[0] && delimitedQueue.isEmpty()) {
                attemptToAdvance[0] = sourceSpliterator.tryAdvance { c ->
                    if (delimiterFilter.invoke(c)) {
                        delimiterQueue.add(c)
                    } else {
                        delimitedQueue.add(c)
                    }
                }
            }
            action.accept(DelimiterCharGroup(delimiterQueue.spliterator()))
            delimiterQueue = LinkedList()
            return attemptToAdvance[0]
        }
        /**
         * Case 2: Next advance should yield the remaining characters of the current
         * char group terminating at the start of the next set of delimiter characters
         * => Consume remaining delimited group, empty delimited queue, and replace delimited queue
         */
        if (delimitedQueue.isNotEmpty()) {
            while (attemptToAdvance[0] && delimiterQueue.isEmpty()) {
                attemptToAdvance[0] = sourceSpliterator.tryAdvance { c ->
                    if (delimiterFilter.invoke(c)) {
                        delimiterQueue.add(c)
                    } else {
                        delimitedQueue.add(c)
                    }
                }
            }
            action.accept(DelimitedCharGroup(delimitedQueue.spliterator()))
            delimitedQueue = LinkedList()
            return attemptToAdvance[0]
        }

        /**
         * Case 3: Consume the first group of characters
         * either all delimiters or those surrounded or terminated by
         * a delimiter character
         * => Consume first character and based on that character, capture the first
         * expected group type, empty that group type's queue, and replace with new
         */

        val delimiterFirstChar = booleanArrayOf(false)
        val firstCharGroupTypeConsumed = booleanArrayOf(false)
        while (attemptToAdvance[0] && !firstCharGroupTypeConsumed[0]) {
            attemptToAdvance[0] = sourceSpliterator.tryAdvance { c ->
                if (delimiterQueue.isEmpty() && delimitedQueue.isEmpty()) {
                    delimiterFirstChar[0] = delimiterFilter.invoke(c)
                }
                if (delimiterFilter.invoke(c)) {
                    delimiterQueue.add(c)
                } else {
                    delimitedQueue.add(c)
                }
            }
            firstCharGroupTypeConsumed[0] = delimiterQueue.isNotEmpty() && delimitedQueue.isNotEmpty()
        }
        return if (delimiterFirstChar[0] && delimiterQueue.isNotEmpty()) {
            action.accept(DelimiterCharGroup(delimiterQueue.spliterator()))
            delimiterQueue = LinkedList()
            attemptToAdvance[0]
        } else if (!delimiterFirstChar[0] && delimitedQueue.isNotEmpty()) {
            action.accept(DelimitedCharGroup(delimitedQueue.spliterator()))
            delimitedQueue = LinkedList()
            attemptToAdvance[0]
        } else {
            attemptToAdvance[0]
        }
    }

    override fun trySplit(): Spliterator<CharSequence>? {
        /**
         * Cannot split given number of expected char groups is not known at initialization
         */
        return null
    }

    override fun estimateSize(): Long {
        return DEFAULT_SIZE_IF_UNKNOWN
    }

    override fun characteristics(): Int {
        return characteristicsBitSet
    }

    override fun getComparator(): Comparator<CharSequence> {
        throw IllegalStateException()
    }
}