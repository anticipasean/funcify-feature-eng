package funcify.naming.convention

import funcify.naming.rule.NamingRule
import kotlinx.collections.immutable.ImmutableSet


/**
 *
 * @author smccarron
 * @created 3/15/22
 */
interface NamingConvention : (String) -> Boolean {

    val conventionName: String

    val rules: ImmutableSet<NamingRule>

    override fun invoke(input: String): Boolean {
        return rules.stream()
                .parallel()
                .allMatch { r ->
                    r.invoke(input)
                }
    }
}