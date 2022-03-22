package funcify.naming.rule

import funcify.naming.charseq.operation.CharSequenceOperation


/**
 *
 * @author smccarron
 * @created 3/17/22
 */
interface NamingRuleFactory {

    companion object {

        val defaultFactory: NamingRuleFactory by lazy { DefaultNamingRuleFactory }

    }

    fun <CS, CSI> createNamingRule(description: String,
                                   operation: CharSequenceOperation<CS, CSI>): NamingRule<CS, CSI>


}