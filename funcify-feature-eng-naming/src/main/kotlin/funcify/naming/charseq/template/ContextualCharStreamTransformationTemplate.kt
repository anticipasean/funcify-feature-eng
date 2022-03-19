package funcify.naming.charseq.template

import funcify.naming.charseq.context.IndexedChar
import funcify.naming.charseq.group.ContextualCharGroup
import funcify.naming.charseq.spliterator.DelimiterGroupingSpliterator
import java.util.stream.Stream
import java.util.stream.StreamSupport


/**
 *
 * @author smccarron
 * @created 3/15/22
 */
interface ContextualCharStreamTransformationTemplate : CharSequenceTransformationTemplate<Stream<IndexedChar>, Stream<ContextualCharGroup>> {

    override fun emptyCharSeq(): Stream<IndexedChar> {
        TODO("Not yet implemented")
    }

    override fun headCharSeqFromIterableOrEmpty(charSeqIterable: Stream<ContextualCharGroup>): Stream<IndexedChar> {
        TODO("Not yet implemented")
    }

    override fun singletonCharSeqIterable(charSeq: Stream<IndexedChar>): Stream<ContextualCharGroup> {
        TODO("Not yet implemented")
    }

    override fun map(charSeq: Stream<IndexedChar>,
                     mapper: (Char) -> Char): Stream<IndexedChar> {
        TODO("Not yet implemented")
    }

    override fun mapWithIndex(charSeq: Stream<IndexedChar>,
                              mapper: (Int, Char) -> Char): Stream<IndexedChar> {
        TODO("Not yet implemented")
    }

    override fun groupByDelimiter(charSeq: Stream<IndexedChar>,
                                  delimiter: Char): Stream<ContextualCharGroup> {
        return StreamSupport.stream(DelimiterGroupingSpliterator(charSeq.spliterator(),
                                                                 { c -> c == delimiter }),
                                    false)
    }

    override fun groupByStartAndEnd(charSeq: Stream<IndexedChar>,
                                    startStr: String,
                                    endStr: String): Stream<ContextualCharGroup> {
        TODO("Not yet implemented")
    }

    override fun flatMapCharSeqIterable(charSeqIterable: Stream<ContextualCharGroup>,
                                        mapper: (Stream<ContextualCharGroup>) -> Stream<IndexedChar>): Stream<IndexedChar> {
        TODO("Not yet implemented")
    }
}