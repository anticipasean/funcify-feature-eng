package funcify.naming.charseq

import arrow.core.Either


/**
 *
 * @author smccarron
 * @created 3/12/22
 */
class CharArrayWindowCharSeqOperator<CS, CSI>(override val charSeq: Either<CSI, CS>,
                                              private val windowSize: UInt,
                                              private val mapper: (Array<Char>) -> Char) : CharSequenceDesign<CS, CSI> {

    override fun <CST : CharSequenceTemplate<CS, CSI>> fold(template: CST): CS {
        return when (charSeq) {
            is Either.Left -> TODO()
            is Either.Right -> template.mapWithCharArrayWindow(charSeq.value,
                                                               windowSize,
                                                               mapper)
        }
    }

}