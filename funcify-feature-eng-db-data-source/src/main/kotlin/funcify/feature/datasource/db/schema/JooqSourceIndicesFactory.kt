package funcify.feature.datasource.db.schema

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import org.slf4j.Logger
import org.slf4j.LoggerFactory


/**
 *
 * @author smccarron
 * @created 2/27/22
 */
class JooqSourceIndicesFactory {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(JooqSourceIndicesFactory::class.java)
    }

    fun createJooqSourceIndicesFromDatabase(database: org.jooq.meta.Database): ImmutableMap<JooqSourceContainerType, ImmutableList<JooqSourceAttribute>> {
        logger.info("create_jooq_source_indices_from_database: [ database.input_catalogs: {} ]",
                    database.inputCatalogs.joinToString(separator = ", "))

        return persistentMapOf()
    }

}