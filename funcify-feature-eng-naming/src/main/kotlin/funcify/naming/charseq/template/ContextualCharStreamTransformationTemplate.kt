package funcify.naming.charseq.template

import funcify.naming.charseq.context.IndexedChar
import funcify.naming.charseq.group.ContextualCharGroup
import java.util.stream.Stream


/**
 *
 * @author smccarron
 * @created 3/15/22
 */
interface ContextualCharStreamTransformationTemplate : CharSequenceTransformationTemplate<Stream<IndexedChar>, Stream<ContextualCharGroup>> {

    //    override fun emptyCharSeq(): Stream<IndexedChar> {
    //        TODO("Not yet implemented")
    //    }
    //
    //    override fun headCharSeqFromIterableOrEmpty(charSeqIterable: Stream<ContextualCharGroup>): Stream<IndexedChar> {
    //        TODO("Not yet implemented")
    //    }
    //
    //    override fun singletonCharSeqIterable(charSeq: Stream<IndexedChar>): Stream<ContextualCharGroup> {
    //        TODO("Not yet implemented")
    //    }


}