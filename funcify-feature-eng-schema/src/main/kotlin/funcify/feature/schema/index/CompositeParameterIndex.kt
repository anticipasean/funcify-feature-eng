package funcify.feature.schema.index

import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.SourceType

/**
 *
 * @author smccarron
 * @created 2022-06-18
 */
interface CompositeParameterIndex {

    val conventionalName: ConventionalName

    fun canBeProvidedTo(sourceType: SourceType): Boolean

    fun canBeProvidedTo(sourceName: String): Boolean

}
