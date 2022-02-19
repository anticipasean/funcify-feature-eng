package funcify.feature.datasource.db.context

import org.springframework.data.r2dbc.mapping.R2dbcMappingContext
import org.springframework.data.relational.core.mapping.NamingStrategy


/**
 *
 * @author smccarron
 * @created 2/16/22
 */
class R2DbcDatasourceMappingContext(namingStrategy: NamingStrategy) : R2dbcMappingContext(namingStrategy) {

}