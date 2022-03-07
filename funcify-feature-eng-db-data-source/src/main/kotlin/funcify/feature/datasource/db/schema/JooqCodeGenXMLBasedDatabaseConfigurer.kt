package funcify.feature.datasource.db.schema

import org.jooq.meta.Database
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource


/**
 *
 * @author smccarron
 * @created 3/5/22
 */
class JooqCodeGenXMLBasedDatabaseConfigurer(val codeGenXMLResource: ClassPathResource) : JooqMetadataGatheringDatabaseConfigurer {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(JooqCodeGenXMLBasedDatabaseConfigurer::class.java)

    }

    override fun invoke(db: Database): Database {
        if (logger.isDebugEnabled) {
            logger.debug("${}")
        }
    }


}