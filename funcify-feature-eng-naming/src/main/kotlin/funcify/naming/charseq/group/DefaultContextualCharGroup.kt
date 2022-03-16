package funcify.naming.charseq.group

import funcify.naming.charseq.context.IndexedChar
import java.util.Spliterator


/**
 *
 * @author smccarron
 * @created 3/15/22
 */
data class DefaultContextualCharGroup(override val groupSpliterator: Spliterator<IndexedChar>) : ContextualCharGroup {

}
