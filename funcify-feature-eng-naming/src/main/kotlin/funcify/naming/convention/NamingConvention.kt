package funcify.naming.convention

import funcify.naming.ConventionalName
import funcify.naming.rule.NamingRule
import kotlinx.collections.immutable.ImmutableSet


/**
 *
 * @author smccarron
 * @created 3/15/22
 */
interface NamingConvention<in I> {

    val conventionName: String

    val rules: ImmutableSet<NamingRule<*>>

    fun <I> deriveName(input: I): ConventionalName

}