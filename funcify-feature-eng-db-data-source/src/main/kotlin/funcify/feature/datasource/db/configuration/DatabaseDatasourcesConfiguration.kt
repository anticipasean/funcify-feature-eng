package funcify.feature.datasource.db.configuration

import funcify.feature.datasource.db.mssql.SQLServerConnectionProperties
import io.r2dbc.mssql.MssqlConnectionConfiguration
import io.r2dbc.mssql.MssqlConnectionFactory
import io.r2dbc.spi.ConnectionFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


/**
 *
 * @author smccarron
 * @created 2/16/22
 */
@Configuration
class DatabaseDatasourcesConfiguration {

    @ConditionalOnBean(value = [SQLServerConnectionProperties::class])
    @Bean
    fun sqlServerConnectionConfiguration(@Value("\${spring.application.name:svc-ml-features}")
                                         applicationName: String,
                                         sqlServerConnectionProperties: SQLServerConnectionProperties): MssqlConnectionConfiguration {
        return MssqlConnectionConfiguration.builder()
                .applicationName(applicationName)
                .connectTimeout(sqlServerConnectionProperties.connectTimeout)
                .database(sqlServerConnectionProperties.database)
                .host(sqlServerConnectionProperties.host)
                .hostNameInCertificate(sqlServerConnectionProperties.hostNameInCertificate)
                .password(sqlServerConnectionProperties.password)
                .port(sqlServerConnectionProperties.port)
                .sendStringParametersAsUnicode(sqlServerConnectionProperties.sendStringParametersAsUnicode)
                .username(sqlServerConnectionProperties.username)
                .build();
    }

    @ConditionalOnBean(value = [MssqlConnectionConfiguration::class])
    @Bean
    fun connectionFactory(mssqlConnectionConfiguration: MssqlConnectionConfiguration): ConnectionFactory {
        return MssqlConnectionFactory(mssqlConnectionConfiguration)
    }
}