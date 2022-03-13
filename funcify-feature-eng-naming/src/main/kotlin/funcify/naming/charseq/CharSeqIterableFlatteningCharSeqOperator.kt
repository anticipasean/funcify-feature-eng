package funcify.naming.charseq

import arrow.core.Either


/**
 *
 * @author smccarron
 * @created 3/12/22
 */
class CharSeqIterableFlatteningCharSeqOperator<CS, CSI>(override val charSeq: Either<CSI, CS>,
                                                        private val mapper: (CSI) -> CS) : CharSequenceDesign<CS, CSI> {

    override fun <CST : CharSequenceTemplate<CS, CSI>> fold(template: CST): CS {
        return when (charSeq) {
            is Either.Left -> template.flatMapCharSeqIterable(charSeq.value,
                                                              mapper)
            is Either.Right -> template.flatMapCharSeqIterable(template.singletonCharSeqIterable(charSeq.value),
                                                               mapper)
        }
    }

}