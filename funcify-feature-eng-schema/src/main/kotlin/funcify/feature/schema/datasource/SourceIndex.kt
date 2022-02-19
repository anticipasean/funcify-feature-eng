package funcify.feature.schema.datasource

import funcify.feature.schema.SchematicPath


/**
 * A reference with [SchematicPath] from a type of data source
 * that is the same or close enough across all declared data sources
 * of this reference
 * @author smccarron
 * @created 1/30/22
 */
interface SourceIndex {

    fun getDataSourceType(): DataSourceType

    fun getName(): String

    fun getCanonicalPath(): SchematicPath

}