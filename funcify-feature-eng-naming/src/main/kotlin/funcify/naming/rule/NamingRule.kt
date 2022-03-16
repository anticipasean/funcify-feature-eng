package funcify.naming.rule

import funcify.naming.condition.NameCondition
import kotlinx.collections.immutable.ImmutableSet


/**
 *
 * @author smccarron
 * @created 3/15/22
 */
interface NamingRule : (String) -> Boolean {

    val ruleName: String

    val conditions: ImmutableSet<NameCondition>

    override fun invoke(input: String): Boolean {
        return conditions.stream()
                .parallel()
                .allMatch { cond ->
                    cond.invoke(input)
                }
    }
}