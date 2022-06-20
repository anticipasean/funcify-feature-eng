package funcify.feature.datasource.db.schema

import funcify.feature.schema.datasource.DataSource
import funcify.feature.schema.datasource.DataSourceType
import funcify.feature.schema.datasource.RawDataSourceType
import kotlin.reflect.KClass

/**
 *
 * @author smccarron
 * @created 2022-06-20
 */
data class JooqDataSourceKey(override val name: String) : DataSource.Key<RelDatabaseSourceIndex> {

    override val sourceIndexType: KClass<RelDatabaseSourceIndex> = RelDatabaseSourceIndex::class

    override val dataSourceType: DataSourceType = RawDataSourceType.RELATIONAL_DATABASE
}
