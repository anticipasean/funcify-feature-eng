package funcify.feature.datasource.db.schema

import funcify.feature.schema.datasource.SourceIndex

/**
 *
 * @author smccarron
 * @created 1/30/22
 */
interface RelDatabaseSourceIndex : SourceIndex<RelDatabaseSourceIndex> {

    val relTableIdentifier: RelTableIdentifier
}
