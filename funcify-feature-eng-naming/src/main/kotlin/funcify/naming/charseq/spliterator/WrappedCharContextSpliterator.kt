package funcify.naming.charseq.spliterator

import funcify.naming.charseq.context.IndexedChar
import java.util.Spliterator
import java.util.function.Consumer


/**
 *
 * @author smccarron
 * @created 3/15/22
 */
internal data class WrappedCharContextSpliterator(val spliterator: Spliterator<IndexedChar>) : ContextualCharSpliterator {

    override fun forEachRemaining(action: Consumer<in IndexedChar>?) {
        spliterator.forEachRemaining(action)
    }

    override fun getExactSizeIfKnown(): Long {
        return spliterator.exactSizeIfKnown
    }

    override fun hasCharacteristics(characteristics: Int): Boolean {
        return spliterator.hasCharacteristics(characteristics)
    }

    override fun getComparator(): Comparator<in IndexedChar>? {
        return spliterator.comparator
    }

    override fun tryAdvance(action: Consumer<in IndexedChar>?): Boolean {
        return spliterator.tryAdvance(action)
    }

    override fun trySplit(): Spliterator<IndexedChar>? {
        return spliterator.trySplit()
    }

    override fun estimateSize(): Long {
        return spliterator.estimateSize()
    }

    override fun characteristics(): Int {
        return spliterator.characteristics()
    }


}