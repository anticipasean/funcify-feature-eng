package funcify.naming.factory

import funcify.naming.ConventionalName
import funcify.naming.registry.NamingConventionRegistry


/**
 *
 * @author smccarron
 * @created 3/16/22
 */
interface ConventionalNameFactory<NCK, CTX> {

    val registry: NamingConventionRegistry<NCK>

    fun createConventionalNameFor(namingConventionKey: NCK,
                                  context: CTX): ConventionalName

}