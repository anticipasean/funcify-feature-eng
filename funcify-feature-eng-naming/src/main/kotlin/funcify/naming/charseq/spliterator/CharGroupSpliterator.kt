package funcify.naming.charseq.spliterator

import funcify.naming.charseq.group.CharGroup
import java.util.Spliterator


/**
 *
 * @author smccarron
 * @created 3/12/22
 */
interface CharGroupSpliterator : Spliterator<CharGroup> {

    val sourceSpliterator: Spliterator<Char>

}