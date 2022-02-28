package funcify.feature.datasource.db.configuration

import io.r2dbc.spi.ConnectionFactory
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


/**
 *
 * @author smccarron
 * @created 2/16/22
 */
@Configuration
class DatabaseDatasourcesConfiguration {


    @Bean
    fun jooqDslContext(connectionFactory: ConnectionFactory): DSLContext {
        return DSL.using(connectionFactory)
    }


}