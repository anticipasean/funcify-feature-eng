package funcify.naming.convention

import funcify.naming.ConventionalName


/**
 *
 * @author smccarron
 * @created 3/15/22
 */
interface NamingConvention<in I> {

    val conventionName: String

    fun <I> deriveName(input: I): ConventionalName

}