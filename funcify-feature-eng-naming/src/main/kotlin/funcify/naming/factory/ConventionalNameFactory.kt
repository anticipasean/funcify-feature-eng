package funcify.naming.factory

import funcify.naming.ConventionalName
import funcify.naming.registry.NamingConventionRegistry


/**
 *
 * @author smccarron
 * @created 3/16/22
 */
interface ConventionalNameFactory<I> {

    val registry: NamingConventionRegistry

    fun createConventionalNameFor(conventionName: String,
                                  input: I): ConventionalName?

}