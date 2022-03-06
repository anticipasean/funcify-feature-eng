package funcify.feature.datasource.db.schema

import org.jooq.meta.Database


/**
 *
 * @author smccarron
 * @created 3/5/22
 */
interface JooqMetadataGatheringDatabaseConfigurer : (Database) -> Database {

    companion object {
        val NO_CONFIGURATION_CHANGES_INSTANCE by lazy { { db: Database -> db } as JooqMetadataGatheringDatabaseConfigurer }
    }

    override fun invoke(db: Database): Database

}