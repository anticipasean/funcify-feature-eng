package funcify.naming.charseq.operation


/**
 *
 * @author smccarron
 * @created 3/19/22
 */
interface CharSequenceFilterOperation<CSI> : (CSI) -> Boolean {

    override fun invoke(input: CSI): Boolean

}