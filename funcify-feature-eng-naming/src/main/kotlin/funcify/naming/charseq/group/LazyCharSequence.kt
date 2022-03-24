package funcify.naming.charseq.group

import java.util.Spliterator


/**
 *
 * @author smccarron
 * @created 3/24/22
 */
internal data class LazyCharSequence(private val inputSpliterator: Spliterator<Char>) : BaseCharGroup(inputSpliterator) {

}
