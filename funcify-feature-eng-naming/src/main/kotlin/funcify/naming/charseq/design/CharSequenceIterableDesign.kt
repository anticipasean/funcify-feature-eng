package funcify.naming.charseq.design

import arrow.core.Either
import funcify.naming.charseq.template.CharSequenceTransformationTemplate
import funcify.naming.charseq.operator.CharSeqIterableFlatteningCharSeqOperator


/**
 *
 * @author smccarron
 * @created 3/12/22
 */
interface CharSequenceIterableDesign<CS, CSI> {

    val charSeq: Either<CSI, CS>

    fun flatMapCharSeqIterable(mapper: (CSI) -> CS): CharSequenceDesign<CS, CSI> {
        return CharSeqIterableFlatteningCharSeqOperator<CS, CSI>(charSeq, mapper)
    }

    fun <CST> fold(template: CST): CSI where CST : CharSequenceTransformationTemplate<CS, CSI>
}