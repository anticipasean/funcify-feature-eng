package funcify.naming.registry

import funcify.naming.convention.NamingConvention
import kotlinx.collections.immutable.ImmutableMap


/**
 *
 * @author smccarron
 * @created 3/16/22
 */
interface NamingConventionRegistry<K> {

    val namingConventionByKey: ImmutableMap<K, NamingConvention>

}