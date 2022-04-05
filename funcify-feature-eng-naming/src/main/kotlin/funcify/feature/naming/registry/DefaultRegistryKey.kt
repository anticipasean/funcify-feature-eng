package funcify.feature.naming.registry

import kotlin.reflect.KClass


/**
 *
 * @author smccarron
 * @created 3/17/22
 */
data class DefaultRegistryKey<I : Any>(override val conventionName: String,
                                       override val inputType: KClass<I>) : RegistryKey<I> {

}
