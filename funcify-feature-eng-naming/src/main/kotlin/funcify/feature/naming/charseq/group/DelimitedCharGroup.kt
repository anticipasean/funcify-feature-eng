package funcify.feature.naming.charseq.group

import java.util.Spliterator


/**
 *
 * @author smccarron
 * @created 3/14/22
 */
internal data class DelimitedCharGroup(private val inputSpliterator: Spliterator<Char>) : BaseCharGroup(inputSpliterator),
                                                                                          DelimiterBasedGrouping {

}