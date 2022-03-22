package funcify.naming.rule

import funcify.naming.charseq.operation.CharSequenceOperation


/**
 *
 * @author smccarron
 * @created 3/15/22
 */
interface NamingRule<CS, CSI> {

    val description: String

    val operation: CharSequenceOperation<CS, CSI>

}