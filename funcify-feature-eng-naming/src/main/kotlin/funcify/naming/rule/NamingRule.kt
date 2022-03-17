package funcify.naming.rule

import funcify.naming.condition.NameCondition
import kotlinx.collections.immutable.ImmutableSet


/**
 *
 * @author smccarron
 * @created 3/15/22
 */
interface NamingRule<I> {

    val ruleName: String

    val conditions: ImmutableSet<NameCondition<I>>

    fun test(input: I): Boolean {
        return conditions.stream()
                .parallel()
                .allMatch { cond ->
                    cond.invoke(input)
                }
    }

    fun onSuccess(input: I): I

    fun onFailure(input: I,
                  error: Throwable? = null): I

}