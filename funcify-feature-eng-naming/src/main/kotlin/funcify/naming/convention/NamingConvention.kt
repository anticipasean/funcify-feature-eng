package funcify.naming.convention

import funcify.naming.ConventionalName
import kotlin.reflect.KClass


/**
 *
 * @author smccarron
 * @created 3/15/22
 */
interface NamingConvention<I : Any> {

    val conventionName: String

    val inputType: KClass<I>

    fun deriveName(input: I): ConventionalName

}