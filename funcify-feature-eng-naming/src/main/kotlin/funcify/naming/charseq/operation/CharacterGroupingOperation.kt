package funcify.naming.charseq.operation


/**
 *
 * @author smccarron
 * @created 3/19/22
 */
interface CharacterGroupingOperation<CS, CSI> : CharSequenceOperation<CS, CSI>,
                                                (CS) -> CSI {

    override fun invoke(input: CS): CSI

}