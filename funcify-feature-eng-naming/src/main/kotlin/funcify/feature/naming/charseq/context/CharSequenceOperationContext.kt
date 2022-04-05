package funcify.feature.naming.charseq.context


/**
 *
 * @author smccarron
 * @created 3/22/22
 */
interface CharSequenceOperationContext<I, CS, CSI> {

    val inputToCharSequenceTransformer: (I) -> CSI


}