package funcify.naming.charseq.spliterator

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
internal class CharSequenceSourceSpliterator(private val charSequence: CharSequence,
                                             private var index: Int = 0,
                                             private val exclusiveLimit: Int = charSequence.length,
                                             private val characteristicsBitSet: Int = DEFAULT_CHARACTERISTICS_BITSET) : Spliterator<Char> {
    companion object {
        internal const val DEFAULT_CHARACTERISTICS_BITSET: Int = SIZED and NONNULL and IMMUTABLE and ORDERED
    }

    private fun cannotAccessCharSequenceWithGivenParameters(): Boolean {
        return charSequence.length == 0 || exclusiveLimit <= 0 || exclusiveLimit > charSequence.length || index < 0 || index >= exclusiveLimit
    }

    override fun tryAdvance(action: Consumer<in Char>?): Boolean {
        if (action == null || cannotAccessCharSequenceWithGivenParameters()) {
            return false
        }
        action.accept(charSequence[index++])
        return true
    }

    override fun trySplit(): Spliterator<Char>? {
        if (cannotAccessCharSequenceWithGivenParameters()) {
            return null
        }
        val lo = index
        val mid = lo + exclusiveLimit ushr 1
        return if (lo >= mid) {
            null
        } else {
            CharSequenceSourceSpliterator(charSequence,
                                          lo,
                                          mid.also { index = it },
                                          characteristicsBitSet)
        }
    }

    override fun forEachRemaining(action: Consumer<in Char>?) {
        if (action == null || cannotAccessCharSequenceWithGivenParameters()) {
            return
        }
        var idx: Int = index
        do {
            action.accept(charSequence[idx])
        } while (++idx < exclusiveLimit)
    }

    override fun estimateSize(): Long {
        if (cannotAccessCharSequenceWithGivenParameters()) {
            return 0L
        }
        return (exclusiveLimit - index).toLong()
    }

    override fun characteristics(): Int {
        return characteristicsBitSet
    }

}