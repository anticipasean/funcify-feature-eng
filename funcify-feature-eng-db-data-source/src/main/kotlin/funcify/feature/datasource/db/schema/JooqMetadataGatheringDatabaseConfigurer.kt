package funcify.feature.datasource.db.schema

import org.jooq.meta.Database

/**
 *
 * @author smccarron
 * @created 3/5/22
 */
fun interface JooqMetadataGatheringDatabaseConfigurer : (Database) -> Database {

    companion object {
        @JvmStatic
        val NO_CONFIGURATION_CHANGES_INSTANCE = JooqMetadataGatheringDatabaseConfigurer { db -> db }
    }

    override fun invoke(db: Database): Database
}
