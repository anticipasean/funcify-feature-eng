package funcify.feature.datasource.reldb

import funcify.feature.datasource.RawDataSourceType
import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.SourceIndex


/**
 *
 * @author smccarron
 * @created 1/30/22
 */
interface RelDatabaseSourceIndex : SourceIndex {

    override val dataSourceType: DataSourceType
        get() = RawDataSourceType.RELATIONAL_DATABASE


    val relTableIdentifier: RelTableIdentifier

}