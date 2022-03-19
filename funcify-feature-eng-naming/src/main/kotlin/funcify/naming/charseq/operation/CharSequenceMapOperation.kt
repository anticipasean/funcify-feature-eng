package funcify.naming.charseq.operation


/**
 *
 * @author smccarron
 * @created 3/12/22
 */
interface CharSequenceMapOperation<CSI> : (CSI) -> CSI {

    override fun invoke(input: CSI): CSI

}