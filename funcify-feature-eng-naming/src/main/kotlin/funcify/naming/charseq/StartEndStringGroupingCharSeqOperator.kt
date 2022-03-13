package funcify.naming.charseq

import arrow.core.Either


/**
 *
 * @author smccarron
 * @created 3/12/22
 */
class StartEndStringGroupingCharSeqOperator<CS, CSI>(override val charSeq: Either<CSI, CS>,
                                                     private val startStr: String,
                                                     private val endStr: String) : CharSequenceIterableDesign<CS, CSI> {

    override fun <CST : CharSequenceTemplate<CS, CSI>> fold(template: CST): CSI {
        return when (charSeq) {
            is Either.Left -> TODO()
            is Either.Right -> template.groupByStartAndEnd(charSeq.value,
                                                           startStr,
                                                           endStr)
        }
    }

}