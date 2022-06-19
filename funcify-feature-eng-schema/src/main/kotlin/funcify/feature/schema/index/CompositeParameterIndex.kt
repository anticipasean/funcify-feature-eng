package funcify.feature.schema.index

import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.DataSourceType

/**
 *
 * @author smccarron
 * @created 2022-06-18
 */
interface CompositeParameterIndex {

    val conventionalName: ConventionalName

    fun canBeProvidedTo(sourceType: DataSourceType): Boolean

    fun canBeProvidedTo(sourceName: String): Boolean

}
