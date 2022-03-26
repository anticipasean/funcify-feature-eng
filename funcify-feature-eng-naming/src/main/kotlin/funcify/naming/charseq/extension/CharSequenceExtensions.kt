package funcify.naming.charseq.extension

import funcify.naming.charseq.group.CharGroup
import funcify.naming.charseq.spliterator.CharSequenceSourceSpliterator
import java.util.Spliterator
import java.util.stream.Stream
import java.util.stream.StreamSupport


/**
 *
 * @author smccarron
 * @created 3/26/22
 */
internal object CharSequenceExtensions {

    fun CharSequence.spliterator(): Spliterator<Char> {
        return when (this) {
            is CharGroup -> {
                return this.spliterator()
            }
            else -> {
                CharSequenceSourceSpliterator(this)
            }
        }
    }

    fun CharSequence.stream(): Stream<out Char> {
        return StreamSupport.stream({ this.spliterator() },
                                    CharSequenceSourceSpliterator.DEFAULT_CHARACTERISTICS_BITSET,
                                    false)
    }

}