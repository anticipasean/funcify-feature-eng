package funcify.feature.naming.charseq.operation


/**
 *
 * @author smccarron
 * @created 3/12/22
 */
interface CharSequenceMapOperation<CS, CSI> : CharSequenceOperation<CS, CSI>,
                                              (CSI) -> CSI {

    override fun invoke(input: CSI): CSI

}