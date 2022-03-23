package funcify.naming.charseq.template

import funcify.naming.charseq.spliterator.CharGroupSpliterator


/**
 *
 * @author smccarron
 * @created 3/12/22
 */
interface CharSeqSpliteratorBasedTemplate<CTX> : CharSequenceContextTransformationTemplate<CTX, ContextualCharSpliterator, CharGroupSpliterator> {

    //    override fun emptyCharSeq(): ContextualCharSpliterator {
    //        return EmptyCharContextSpliterator
    //    }
    //
    //    override fun headCharSeqFromIterableOrEmpty(charSeqIterable: ContextualCharGroupSpliterator): ContextualCharSpliterator {
    //        val firstEntry: Array<Spliterator<IndexedChar>?> = arrayOfNulls<Spliterator<IndexedChar>>(1)
    //        val advanceComplete: Boolean = charSeqIterable.tryAdvance { cc ->
    //            firstEntry[0] = cc.groupSpliterator
    //        }
    //        return if (advanceComplete && firstEntry[0] != null) {
    //            firstEntry[0] as? ContextualCharSpliterator
    //            ?: WrappedCharContextSpliterator(firstEntry[0]!!)
    //        } else {
    //            EmptyCharContextSpliterator
    //        }
    //    }
    //
    //    override fun singletonCharSeqIterable(charSeq: ContextualCharSpliterator): ContextualCharGroupSpliterator {
    //        return SingleCharGroupSpliterator(charSeq)
    //    }


}