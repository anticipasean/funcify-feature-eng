package funcify.feature.datasource.sqldb

import funcify.feature.datasource.RawDataSourceType
import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.SourceIndex


/**
 *
 * @author smccarron
 * @created 1/30/22
 */
interface SqlDatabaseSourceIndex : SourceIndex {

    override val dataSourceType: DataSourceType
        get() = RawDataSourceType.RELATIONAL_DATABASE


    val sqlDatabaseTable: SqlDatabaseTable

}