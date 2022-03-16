package funcify.naming.charseq.spliterator

import funcify.naming.charseq.context.IndexedChar
import funcify.naming.charseq.context.at
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
    }

    private fun cannotAccessArrayWithGivenParameters(): Boolean {
        return charArray.size == 0 || exclusiveLimit <= 0 || exclusiveLimit > charArray.size || index < 0 || index >= exclusiveLimit
    }

    override fun tryAdvance(action: Consumer<in IndexedChar>?): Boolean {
        if (action == null || cannotAccessArrayWithGivenParameters()) {
            return false
        }
        action.accept(charArray at index++)
        return true
    }

    override fun trySplit(): Spliterator<IndexedChar>? {
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

    override fun forEachRemaining(action: Consumer<in IndexedChar>?) {
        if (action == null || cannotAccessArrayWithGivenParameters()) {
            return
        }
        var idx: Int = index
        do {
            action.accept(charArray at idx)
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

    override fun getComparator(): Comparator<in IndexedChar> {
        return IndexedChar.DEFAULT_COMPARATOR
    }

}