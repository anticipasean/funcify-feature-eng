package funcify.feature.datasource.sqldb

import funcify.feature.schema.datasource.SourceAttribute


/**
 *
 * @author smccarron
 * @created 1/30/22
 */
interface SqlDatabaseSourceAttribute : SqlDatabaseSourceIndex, SourceAttribute {

    val columnName: String

}