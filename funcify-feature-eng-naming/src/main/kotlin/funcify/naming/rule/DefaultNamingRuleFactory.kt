package funcify.naming.rule

import funcify.naming.charseq.operation.CharSequenceOperation


/**
 *
 * @author smccarron
 * @created 3/22/22
 */
internal object DefaultNamingRuleFactory : NamingRuleFactory {

    override fun <CS, CSI> createNamingRule(description: String,
                                            operation: CharSequenceOperation<CS, CSI>): NamingRule<CS, CSI> {
        return DefaultNamingRule<CS, CSI>(description,
                                          operation)
    }

    internal data class DefaultNamingRule<CS, CSI>(override val description: String,
                                                   override val operation: CharSequenceOperation<CS, CSI>) : NamingRule<CS, CSI>,
                                                                                                             Comparable<NamingRule<CS, CSI>> {
        companion object {
            private val DEFAULT_COMPARATOR: Comparator<NamingRule<*, *>> by lazy {
                Comparator.comparing(NamingRule<*, *>::description,
                                     Comparator.naturalOrder())
            }
        }

        override fun compareTo(other: NamingRule<CS, CSI>): Int {
            return DEFAULT_COMPARATOR.compare(this,
                                              other)
        }

    }

}