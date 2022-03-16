package funcify.naming.charseq.group

import funcify.naming.charseq.context.IndexedChar
import java.util.Spliterator


/**
 *
 * @author smccarron
 * @created 3/14/22
 */
data class DelimitedCharGroup(override val groupSpliterator: Spliterator<IndexedChar>) : DelimiterBasedGrouping {

}
