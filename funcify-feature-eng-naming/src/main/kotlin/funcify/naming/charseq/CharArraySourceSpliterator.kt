package funcify.naming.charseq

import java.util.Spliterator
import java.util.Spliterator.IMMUTABLE
import java.util.Spliterator.NONNULL
import java.util.Spliterator.ORDERED
import java.util.Spliterator.SIZED
import java.util.function.Consumer


/**
 *
 * @author smccarron
 * @created 3/12/22
 */
internal class CharArraySourceSpliterator(private val charArray: CharArray,
                                          private var index: Int = 0,
                                          private val exclusiveLimit: Int = charArray.size,
                                          private val characteristicsBitSet: Int = DEFAULT_CHARACTERISTICS_BITSET) : ContextualCharSpliterator {
    companion object {
        internal const val DEFAULT_CHARACTERISTICS_BITSET: Int = SIZED and NONNULL and IMMUTABLE and ORDERED

        /**
         * TODO: Handle single character in array case, potentially
         *  updating contract where the first is also the last character
         *  of the predefined sequence
         */
        private fun charContextOfIndex(chars: CharArray,
                                       idx: Int) =
                when (idx) {
                    0 -> {
                        CharContext(chars[idx],
                                    idx,
                                    RelativeCharSequencePosition.FIRST_CHARACTER)
                    }
                    (chars.size - 1) -> {
                        CharContext(chars[idx],
                                    idx,
                                    RelativeCharSequencePosition.LAST_CHARACTER)
                    }
                    else -> {
                        CharContext(chars[idx],
                                    idx,
                                    RelativeCharSequencePosition.MIDDLE_CHARACTER)
                    }
                }
    }

    private fun cannotAccessArrayWithGivenParameters(): Boolean {
        return charArray.size == 0 || exclusiveLimit <= 0 || exclusiveLimit > charArray.size || index < 0 || index >= exclusiveLimit
    }

    override fun tryAdvance(action: Consumer<in CharContext>?): Boolean {
        if (action == null || cannotAccessArrayWithGivenParameters()) {
            return false
        }
        action.accept(Companion.charContextOfIndex(charArray,
                                                   index++))
        return true
    }

    override fun trySplit(): Spliterator<CharContext>? {
        if (cannotAccessArrayWithGivenParameters()) {
            return null
        }
        val lo = index
        val mid = lo + exclusiveLimit ushr 1
        return if (lo >= mid) {
            null
        } else {
            CharArraySourceSpliterator(charArray,
                                       lo,
                                       mid.also { index = it },
                                       characteristicsBitSet)
        }
    }

    override fun forEachRemaining(action: Consumer<in CharContext>?) {
        if (action == null || cannotAccessArrayWithGivenParameters()) {
            return
        }
        var idx: Int = index
        do {
            action.accept(charContextOfIndex(charArray,
                                             idx))
        } while (++idx < exclusiveLimit)
    }

    override fun estimateSize(): Long {
        if (cannotAccessArrayWithGivenParameters()) {
            return 0L
        }
        return (exclusiveLimit - index).toLong()
    }

    override fun characteristics(): Int {
        return characteristicsBitSet
    }

    override fun getComparator(): Comparator<in CharContext> {
        return CharContext.DEFAULT_COMPARATOR
    }

}