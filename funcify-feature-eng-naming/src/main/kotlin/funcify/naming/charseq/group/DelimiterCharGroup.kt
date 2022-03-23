package funcify.naming.charseq.group

import java.util.Spliterator


/**
 *
 * @author smccarron
 * @created 3/14/22
 */
internal class DelimiterCharGroup(private val inputSpliterator: Spliterator<Char>) : BaseCharGroup(inputSpliterator),
                                                                                     DelimiterBasedGrouping {

}
