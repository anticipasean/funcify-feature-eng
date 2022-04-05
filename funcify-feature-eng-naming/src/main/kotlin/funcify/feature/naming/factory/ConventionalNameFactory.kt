package funcify.feature.naming.factory

import funcify.feature.naming.ConventionalName
import funcify.feature.naming.registry.NamingConventionRegistry


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