package funcify.naming.charseq

import java.util.Spliterator
import java.util.Spliterators


/**
 *
 * @author smccarron
 * @created 3/14/22
 */
interface ContextualCharGroup : Iterable<CharContext> {

    val groupSpliterator: Spliterator<CharContext>

    override fun iterator(): Iterator<CharContext> {
        return Spliterators.iterator(groupSpliterator)
    }

    override fun spliterator(): Spliterator<CharContext> {
        return groupSpliterator
    }
}