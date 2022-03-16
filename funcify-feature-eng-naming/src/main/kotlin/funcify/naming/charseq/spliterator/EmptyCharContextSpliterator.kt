package funcify.naming.charseq.spliterator

import funcify.naming.charseq.context.IndexedChar
import java.util.Spliterator
import java.util.Spliterator.IMMUTABLE
import java.util.Spliterator.NONNULL
import java.util.Spliterator.ORDERED
import java.util.Spliterator.SIZED
import java.util.Spliterator.SORTED
import java.util.function.Consumer


/**
 *
 * @author smccarron
 * @created 3/15/22
 */
object EmptyCharContextSpliterator : ContextualCharSpliterator {

    private const val DEFAULT_CHARACTERISTICS_BITSET: Int = NONNULL and SIZED and IMMUTABLE and SORTED and ORDERED

    override fun tryAdvance(action: Consumer<in IndexedChar>?): Boolean {
        return false
    }

    override fun trySplit(): Spliterator<IndexedChar>? {
        return null
    }

    override fun estimateSize(): Long {
        return 0L
    }

    override fun characteristics(): Int {
        return DEFAULT_CHARACTERISTICS_BITSET
    }

}