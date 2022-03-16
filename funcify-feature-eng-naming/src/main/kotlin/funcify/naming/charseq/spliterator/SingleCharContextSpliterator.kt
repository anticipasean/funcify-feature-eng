package funcify.naming.charseq.spliterator

import funcify.naming.charseq.context.IndexedChar
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
internal class SingleCharContextSpliterator(private val singleCharContext: IndexedChar,
                                            private var expended: Boolean = false,
                                            private val characteristicsBitSet: Int = DEFAULT_CHARACTERISTICS_BITSET) : ContextualCharSpliterator {
    companion object {
        internal const val DEFAULT_CHARACTERISTICS_BITSET: Int = SIZED and NONNULL and IMMUTABLE and ORDERED
    }

    override fun tryAdvance(action: Consumer<in IndexedChar>?): Boolean {
        if (action == null || expended) {
            return false
        }
        action.accept(singleCharContext)
        expended = true
        return true
    }

    override fun trySplit(): Spliterator<IndexedChar>? {
        return null
    }

    override fun forEachRemaining(action: Consumer<in IndexedChar>?) {
        if (action == null || expended) {
            return
        }
        action.accept(singleCharContext)
        expended = true
    }

    override fun estimateSize(): Long {
        return if (expended) {
            0L
        } else {
            1L
        }
    }

    override fun characteristics(): Int {
        return characteristicsBitSet
    }

    override fun getComparator(): Comparator<in IndexedChar> {
        return IndexedChar.DEFAULT_COMPARATOR
    }

}