package funcify.naming.charseq

import java.util.Spliterator


/**
 *
 * @author smccarron
 * @created 3/12/22
 */
interface ContextualCharGroupSpliterator : Spliterator<ContextualCharGroup> {

    val sourceSpliterator: Spliterator<CharContext>

}