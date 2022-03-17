package funcify.naming.convention

import kotlin.reflect.KClass


/**
 *
 * @author smccarron
 * @created 3/17/22
 */
class DefaultNamingConventionFactory() : NamingConventionFactory {

    override fun fromRawString(): NamingConventionFactory.InputDelimiterSpec<String> {
        TODO("Not yet implemented")
    }

    override fun <I : Any> fromInputType(inputType: KClass<I>): NamingConventionFactory.StringExtractionSpec<I> {
        TODO("Not yet implemented")
    }


}