package funcify.naming.charseq.operation


/**
 *
 * @author smccarron
 * @created 3/12/22
 */
interface CharacterMapOperation<CS, CSI> : CharSequenceOperation<CS, CSI>,
                                           (CS) -> CS {

    override fun invoke(input: CS): CS
}