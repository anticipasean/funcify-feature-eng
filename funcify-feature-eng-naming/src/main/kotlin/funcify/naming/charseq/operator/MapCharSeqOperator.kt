package funcify.naming.charseq.operator

import arrow.core.Either
import funcify.naming.charseq.design.CharSequenceDesign
import funcify.naming.charseq.template.CharSequenceTransformationTemplate


/**
 *
 * @author smccarron
 * @created 3/12/22
 */
class MapCharSeqOperator<CS, CSI>(override val charSeq: Either<CSI, CS>,
                                  private val mapper: (Int, Char) -> Char) : CharSequenceDesign<CS, CSI> {

    override fun <CST : CharSequenceTransformationTemplate<CS, CSI>> fold(template: CST): CS {
        return when (charSeq) {
            is Either.Left -> TODO()
            is Either.Right -> template.mapWithIndex(charSeq.value,
                                                     mapper)
        }
    }

}