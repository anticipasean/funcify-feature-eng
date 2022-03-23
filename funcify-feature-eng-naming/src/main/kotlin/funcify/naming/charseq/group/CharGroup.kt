package funcify.naming.charseq.group

import java.util.Spliterator
import java.util.Spliterators


/**
 *
 * @author smccarron
 * @created 3/14/22
 */
interface CharGroup : CharSequence,
                      Iterable<Char> {

    val groupSpliterator: Spliterator<Char>

    override fun iterator(): Iterator<Char> {
        return Spliterators.iterator(groupSpliterator)
    }

    override fun spliterator(): Spliterator<Char> {
        return groupSpliterator
    }

    /**
     * @implementation: Lazily initialize string form of group
     * from group spliterator
     */
    override fun toString(): String
}