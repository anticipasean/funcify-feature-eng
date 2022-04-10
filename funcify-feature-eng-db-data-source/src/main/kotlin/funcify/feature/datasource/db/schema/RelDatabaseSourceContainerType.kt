package funcify.feature.datasource.db.schema

import funcify.feature.schema.datasource.SourceContainerType


/**
 *
 * @author smccarron
 * @created 1/30/22
 */
interface RelDatabaseSourceContainerType : RelDatabaseSourceIndex,
                                           SourceContainerType<RelDatabaseSourceAttribute> {


}