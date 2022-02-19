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

    override fun getDataSourceType(): DataSourceType {
        return RawDataSourceType.RELATIONAL_DATABASE;
    }

    fun getSqlDatabaseTable(): SqlDatabaseTable

}