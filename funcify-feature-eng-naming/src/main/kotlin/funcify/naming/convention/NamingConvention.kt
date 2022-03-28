package funcify.naming.convention

import funcify.naming.ConventionalName


/**
 *
 * @author smccarron
 * @created 3/15/22
 */
interface NamingConvention<I : Any> {

    val conventionName: String

    val conventionKey: Any
    get() = conventionName

    val delimiter: String
    get() = ConventionalName.EMPTY_STRING_DELIMITER

    fun deriveName(input: I): ConventionalName

}