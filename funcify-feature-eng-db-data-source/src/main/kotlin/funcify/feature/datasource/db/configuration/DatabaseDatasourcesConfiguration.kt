package funcify.feature.datasource.db.configuration

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.attempt.TryFactory
import io.r2dbc.spi.ConnectionFactory
import org.jooq.DSLContext
import org.jooq.impl.DefaultConfiguration
import org.springframework.beans.factory.BeanCreationException
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource


/**
 *
 * @author smccarron
 * @created 2/16/22
 */
@Configuration
class DatabaseDatasourcesConfiguration {

    //TODO: Add configuration property for selecting which type to prefer if both are available
    @Bean
    fun jooqDslContext(dataSource: ObjectProvider<DataSource>,
                       connectionFactory: ObjectProvider<ConnectionFactory>): DSLContext {
        val attempt = Try.attemptNullable { dataSource.ifAvailable }
                .flatMap { dsOpt -> Try.fromOption(dsOpt) }
                .map { ds -> ds.left() }
                .fold({ dsLeft -> Try.success(dsLeft) },
                      { thr ->
                          Try.attemptNullable { connectionFactory.ifAvailable }
                                  .flatMap { cfOpt -> Try.fromOption(cfOpt) }
                                  .map { cf -> cf.right() }
                                  .flatMapFailure { otherThr ->
                                      val message = """
                                          |multiple_bean_exceptions: 
                                          |[ message_1: "${thr.message}", 
                                          |message_2: "${otherThr.message}" ]
                                      """.trimMargin()
                                      Try.failure(BeanCreationException(message))
                                  }
                      })
        return when (attempt) {
            is TryFactory.Failure -> {
                throw attempt.throwable
            }
            is TryFactory.Success -> {
                when (val dataSrcOrConnFact = attempt.successObject) {
                    is Either.Left -> {
                        DefaultConfiguration().derive(dataSrcOrConnFact.value)
                                .dsl()
                    }
                    is Either.Right -> {
                        DefaultConfiguration().derive(dataSrcOrConnFact.value)
                                .dsl()
                    }
                }
            }
        }
    }


}