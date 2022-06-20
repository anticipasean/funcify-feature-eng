package funcify.feature.datasource.db.schema

import funcify.feature.schema.datasource.SourceAttribute


/**
 *
 * @author smccarron
 * @created 1/30/22
 */
interface RelDatabaseSourceAttribute : RelDatabaseSourceIndex,
                                       SourceAttribute<RelDatabaseSourceIndex> {

    val columnName: String

}
