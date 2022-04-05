package funcify.feature.naming.charseq.operation


/**
 *
 * @author smccarron
 * @created 3/19/22
 */
interface CharacterGroupFlatteningOperation<CS, CSI> : CharSequenceOperation<CS, CSI>,
                                                       (CSI) -> CS {

    override fun invoke(input: CSI): CS
}