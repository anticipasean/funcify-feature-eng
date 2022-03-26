package funcify.naming.convention

import funcify.naming.ConventionalName
import kotlin.reflect.KClass


/**
 *
 * @author smccarron
 * @created 3/25/22
 */
data class DefaultNamingConvention<I : Any>(override val conventionName: String,
                                            override val inputType: KClass<I>,
                                            private val derivationFunction: (I) -> ConventionalName) : NamingConvention<I> {

    override fun deriveName(input: I): ConventionalName {
        return derivationFunction.invoke(input)
    }
}
