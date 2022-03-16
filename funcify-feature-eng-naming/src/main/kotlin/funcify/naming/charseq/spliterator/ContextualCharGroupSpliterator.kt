package funcify.naming.charseq.spliterator

import funcify.naming.charseq.context.IndexedChar
import funcify.naming.charseq.group.ContextualCharGroup
import java.util.Spliterator


/**
 *
 * @author smccarron
 * @created 3/12/22
 */
interface ContextualCharGroupSpliterator : Spliterator<ContextualCharGroup> {

    val sourceSpliterator: Spliterator<IndexedChar>

}