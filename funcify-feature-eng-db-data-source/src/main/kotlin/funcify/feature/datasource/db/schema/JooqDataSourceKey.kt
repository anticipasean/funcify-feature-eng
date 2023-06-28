package funcify.feature.datasource.db.schema

import funcify.feature.schema.datasource.DataElementSource
import funcify.feature.schema.datasource.SourceType
import funcify.feature.schema.datasource.RawSourceType
import kotlin.reflect.KClass

/**
 *
 * @author smccarron
 * @created 2022-06-20
 */
data class JooqDataSourceKey(override val name: String) : DataElementSource.Key<RelDatabaseSourceIndex> {

    override val sourceIndexType: KClass<RelDatabaseSourceIndex> = RelDatabaseSourceIndex::class

    override val sourceType: SourceType = RawSourceType.RELATIONAL_DATABASE
}
