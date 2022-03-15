package funcify.naming.charseq

import java.util.Spliterator


/**
 *
 * @author smccarron
 * @created 3/14/22
 */
data class DelimiterCharGroup(override val groupSpliterator: Spliterator<CharContext>) : DelimiterBasedGrouping {

}
