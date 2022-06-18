package funcify.feature.schema.index

import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.DataSourceType

/**
 * A container or scalar type with potentially multiple [DataSource]s from which instances mapping
 * to this [ConventionalName] may be sourced
 * @author smccarron
 * @created 2/7/22
 */
interface CompositeSourceIndex {

    val conventionalName: ConventionalName

    fun canBeSourcedFrom(sourceType: DataSourceType): Boolean
}
