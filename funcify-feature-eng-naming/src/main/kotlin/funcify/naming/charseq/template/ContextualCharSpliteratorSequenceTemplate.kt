package funcify.naming.charseq.template

import funcify.naming.charseq.context.IndexedChar
import funcify.naming.charseq.context.RelativeCharSequencePosition
import funcify.naming.charseq.spliterator.ContextualCharGroupSpliterator
import funcify.naming.charseq.spliterator.ContextualCharSpliterator
import funcify.naming.charseq.spliterator.EmptyCharContextSpliterator
import funcify.naming.charseq.spliterator.SingleCharGroupSpliterator
import funcify.naming.charseq.spliterator.WrappedCharContextSpliterator
import java.util.Spliterator


/**
 *
 * @author smccarron
 * @created 3/12/22
 */
interface ContextualCharSpliteratorSequenceTemplate : CharSequenceTemplate<ContextualCharSpliterator, ContextualCharGroupSpliterator> {

    override fun emptyCharSeq(): ContextualCharSpliterator {
        return EmptyCharContextSpliterator
    }

    override fun headCharSeqFromIterableOrEmpty(charSeqIterable: ContextualCharGroupSpliterator): ContextualCharSpliterator {
        val firstEntry: Array<Spliterator<IndexedChar>?> = arrayOfNulls<Spliterator<IndexedChar>>(1)
        val advanceComplete: Boolean = charSeqIterable.tryAdvance { cc ->
            firstEntry[0] = cc.groupSpliterator
        }
        return if (advanceComplete && firstEntry[0] != null) {
            firstEntry[0] as? ContextualCharSpliterator
            ?: WrappedCharContextSpliterator(firstEntry[0]!!)
        } else {
            EmptyCharContextSpliterator
        }
    }

    override fun singletonCharSeqIterable(charSeq: ContextualCharSpliterator): ContextualCharGroupSpliterator {
        return SingleCharGroupSpliterator(charSeq)
    }

    override fun map(charSeq: ContextualCharSpliterator,
                     mapper: (Char) -> Char): ContextualCharSpliterator {
        TODO("Not yet implemented")
    }

    override fun mapWithIndex(charSeq: ContextualCharSpliterator,
                              mapper: (Int, Char) -> Char): ContextualCharSpliterator {
        TODO("Not yet implemented")
    }

    override fun groupByDelimiter(charSeq: ContextualCharSpliterator,
                                  delimiter: Char): ContextualCharGroupSpliterator {
        TODO("Not yet implemented")
    }

    override fun groupByStartAndEnd(charSeq: ContextualCharSpliterator,
                                    startStr: String,
                                    endStr: String): ContextualCharGroupSpliterator {
        TODO("Not yet implemented")
    }

    override fun flatMapCharSeqIterable(charSeqIterable: ContextualCharGroupSpliterator,
                                        mapper: (ContextualCharGroupSpliterator) -> ContextualCharSpliterator): ContextualCharSpliterator {
        TODO("Not yet implemented")
    }
}