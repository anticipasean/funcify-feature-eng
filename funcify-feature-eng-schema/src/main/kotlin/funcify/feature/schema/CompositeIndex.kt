package funcify.feature.schema

import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.DataSourceType

/**
 *
 * @author smccarron
 * @created 2/7/22
 */
interface CompositeIndex {

    val conventionalName: ConventionalName
    fun canBeSourcedFrom(sourceType: DataSourceType): Boolean
}
