package funcify.feature.schema.index

import funcify.feature.naming.ConventionalName
import funcify.feature.schema.datasource.DataElementSource
import funcify.feature.schema.datasource.SourceType

/**
 * A container or scalar type with potentially multiple [DataElementSource]s from which instances mapping
 * to this [ConventionalName] may be sourced
 * @author smccarron
 * @created 2/7/22
 */
interface CompositeSourceIndex {

    val conventionalName: ConventionalName

    fun canBeSourcedFrom(sourceType: SourceType): Boolean

    fun canBeSourcedFrom(sourceName: String): Boolean
}
