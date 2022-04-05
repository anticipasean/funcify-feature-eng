package funcify.feature.naming.registry

import kotlin.reflect.KClass


/**
 *
 * @author smccarron
 * @created 3/17/22
 */
interface RegistryKey<I : Any> {

    val conventionName: String

    val inputType: KClass<I>

}