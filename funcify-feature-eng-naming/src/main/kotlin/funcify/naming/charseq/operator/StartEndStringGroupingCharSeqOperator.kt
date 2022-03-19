package funcify.naming.charseq.operator

import arrow.core.Either
import funcify.naming.charseq.design.CharSequenceIterableDesign
import funcify.naming.charseq.template.CharSequenceTransformationTemplate


/**
 *
 * @author smccarron
 * @created 3/12/22
 */
class StartEndStringGroupingCharSeqOperator<CS, CSI>(override val charSeq: Either<CSI, CS>,
                                                     private val startStr: String,
                                                     private val endStr: String) : CharSequenceIterableDesign<CS, CSI> {

    override fun <CST : CharSequenceTransformationTemplate<CS, CSI>> fold(template: CST): CSI {
        return when (charSeq) {
            is Either.Left -> TODO()
            is Either.Right -> template.groupByStartAndEnd(charSeq.value,
                                                           startStr,
                                                           endStr)
        }
    }

}