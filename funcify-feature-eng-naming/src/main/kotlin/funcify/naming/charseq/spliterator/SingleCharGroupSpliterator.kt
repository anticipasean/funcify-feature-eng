package funcify.naming.charseq.spliterator

import funcify.naming.charseq.context.IndexedChar
import funcify.naming.charseq.group.ContextualCharGroup
import funcify.naming.charseq.group.DefaultContextualCharGroup
import java.util.Spliterator
import java.util.Spliterator.IMMUTABLE
import java.util.Spliterator.NONNULL
import java.util.Spliterator.SIZED
import java.util.function.Consumer


/**
 *
 * @author smccarron
 * @created 3/15/22
 */
internal data class SingleCharGroupSpliterator(override val sourceSpliterator: Spliterator<IndexedChar>,
                                               private var expended: Boolean = false) : ContextualCharGroupSpliterator {

    companion object {
        // Not ordered (no clear comparison of charcontextgroups) but is sized => only one
        private const val DEFAULT_CHARACTERISTICS: Int = IMMUTABLE and NONNULL and SIZED
    }

    override fun tryAdvance(action: Consumer<in ContextualCharGroup>?): Boolean {
        if (action == null || expended) {
            return false
        }
        action.accept(DefaultContextualCharGroup(sourceSpliterator))
        expended = true
        return true
    }

    override fun trySplit(): Spliterator<ContextualCharGroup>? { // cannot split chargroups since there is only one
        return null
    }

    override fun forEachRemaining(action: Consumer<in ContextualCharGroup>?) {
        if (action == null || expended) {
            return
        }
        action.accept(DefaultContextualCharGroup(sourceSpliterator))
        expended = true
    }

    override fun getComparator(): Comparator<in ContextualCharGroup>? {
        return null
    }

    override fun estimateSize(): Long {
        return if (expended) {
            0L
        } else {
            1L
        }
    }

    override fun characteristics(): Int {
        return DEFAULT_CHARACTERISTICS
    }


}
