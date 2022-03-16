package funcify.naming.charseq.group

import funcify.naming.charseq.context.IndexedChar
import java.util.Spliterator
import java.util.Spliterators


/**
 *
 * @author smccarron
 * @created 3/14/22
 */
interface ContextualCharGroup : Iterable<IndexedChar> {

    val groupSpliterator: Spliterator<IndexedChar>

    override fun iterator(): Iterator<IndexedChar> {
        return Spliterators.iterator(groupSpliterator)
    }

    override fun spliterator(): Spliterator<IndexedChar> {
        return groupSpliterator
    }
}