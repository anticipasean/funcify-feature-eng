package funcify.feature.schema

import funcify.feature.schema.datasource.DataSourceType
import funcify.naming.ConventionalName


/**
 *
 * @author smccarron
 * @created 2/7/22
 */
interface CompositeIndex {

    val conventionalName: ConventionalName

    fun canBeSourcedFrom(sourceType: DataSourceType): Boolean

}