package funcify.feature.datasource.db.schema

import org.jooq.meta.Database
import org.springframework.core.io.ClassPathResource


/**
 *
 * @author smccarron
 * @created 3/5/22
 */
class JooqCodeGenXMLBasedDatabaseConfigurer(val codeGenXMLResource: ClassPathResource) : JooqMetadataGatheringDatabaseConfigurer {


    override fun invoke(db: Database): Database {
        TODO("Not yet implemented")
    }


}