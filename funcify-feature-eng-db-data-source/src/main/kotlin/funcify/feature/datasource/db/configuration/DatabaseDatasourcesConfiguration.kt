package funcify.feature.datasource.db.configuration

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import funcify.feature.datasource.db.schema.JooqCodeGenXMLBasedDatabaseConfigurer
import funcify.feature.datasource.db.schema.JooqMetadataGatheringDatabaseConfigurer
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.container.attempt.Try.Companion.flatMapFailure
import funcify.feature.tools.extensions.StringExtensions.flattenIntoOneLine
import io.r2dbc.spi.ConnectionFactory
import java.sql.Connection
import javax.sql.DataSource
import org.jooq.DSLContext
import org.jooq.impl.DefaultConfiguration
import org.jooq.meta.Database
import org.jooq.meta.Databases
import org.springframework.beans.factory.BeanCreationException
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource

/**
 *
 * @author smccarron
 * @created 2/16/22
 */
@Configuration
@ConfigurationProperties(prefix = "funcify-feature-eng.datasources.reldb.jooq")
class DatabaseDatasourcesConfiguration {

    companion object {
        const val CONFIGURATION_PROPERTIES_PREFIX: String =
            "funcify-feature-eng.datasources.reldb.jooq"
    }

    // TODO: Add configuration property for selecting which type to prefer if both are available
    @ConditionalOnMissingBean(value = [DSLContext::class])
    @Bean
    fun jooqDslContext(
        dataSource: ObjectProvider<DataSource>,
        connectionFactory: ObjectProvider<ConnectionFactory>
    ): DSLContext {
        val attempt =
            Try.attemptNullable { dataSource.ifAvailable }
                .flatMap { dsOpt -> Try.fromOption(dsOpt) }
                .map { ds -> ds.left() }
                .fold(
                    { dsLeft -> Try.success(dsLeft) },
                    { thr ->
                        Try.attemptNullable { connectionFactory.ifAvailable }
                            .flatMap { cfOpt -> Try.fromOption(cfOpt) }
                            .map { cf -> cf.right() }
                            .flatMapFailure { otherThr ->
                                val message =
                                    """
                                          |multiple_bean_exceptions: 
                                          |[ message_1: "${thr.message}", 
                                          |message_2: "${otherThr.message}" ]
                                      """.flattenIntoOneLine()
                                Try.failure(BeanCreationException(message))
                            }
                    }
                )
        return attempt.fold(
            { s ->
                when (val dataSrcOrConnFact = s) {
                    is Either.Left -> {
                        DefaultConfiguration().derive(dataSrcOrConnFact.value).dsl()
                    }
                    is Either.Right -> {
                        DefaultConfiguration().derive(dataSrcOrConnFact.value).dsl()
                    }
                }
            },
            { t -> throw t }
        )
    }

    @ConditionalOnBean(value = [DSLContext::class])
    @ConditionalOnMissingBean(value = [JooqMetadataGatheringDatabaseConfigurer::class])
    @Bean
    fun jooqMetadataGatheringDatabaseConfigurer(): JooqMetadataGatheringDatabaseConfigurer {
        return JooqMetadataGatheringDatabaseConfigurer.NO_CONFIGURATION_CHANGES_INSTANCE
    }

    @ConditionalOnBean(value = [DSLContext::class])
    @ConditionalOnProperty(
        prefix = CONFIGURATION_PROPERTIES_PREFIX,
        value = ["code-gen-xml-resource"]
    )
    @Bean
    fun jooqCodeGenXmlBasedDatabaseConfigurer(
        @Value("\${$CONFIGURATION_PROPERTIES_PREFIX.code-gen-xml-resource:}")
        codeGenXmlResourceName: String
    ): JooqMetadataGatheringDatabaseConfigurer {
        return Try.success(codeGenXmlResourceName)
            .filter(
                { n -> n.endsWith(".xml") },
                { n ->
                    val message: String =
                        """
                                |code-gen-xml-resource property value does not 
                                |end in file extension for xml (.xml) 
                                |[ code-gen-xml-resource: "${n}" ] 
                                |cannot create jooqCodeGenXmlBasedDatabaseConfigurer 
                                |instance
                            """.flattenIntoOneLine()
                    BeanCreationException(message)
                }
            )
            .map { n -> ClassPathResource(n) }
            .filter(
                { cpr -> cpr.exists() },
                { cpr ->
                    val message: String =
                        """
                                |code-gen-xml-resource path does not exist 
                                |[ code-gen-xml-resource path: "${cpr.path}" ] 
                                |cannot create jooqCodeGenXmlBasedDatabaseConfigurer 
                                |instance
                            """.flattenIntoOneLine()
                    BeanCreationException(message)
                }
            )
            .map { cpr -> JooqCodeGenXMLBasedDatabaseConfigurer(cpr) }
            .fold({ dc -> dc }, { thr -> throw thr })
    }

    @ConditionalOnBean(value = [DSLContext::class])
    @ConditionalOnMissingBean(value = [Database::class])
    @Bean
    fun jooqMetadataGatheringDatabase(
        jooqDSLContext: DSLContext,
        metadataGatheringDatabaseConfigurer: JooqMetadataGatheringDatabaseConfigurer
    ): Database {
        return Try.attempt { jooqDSLContext.parsingConnection() }
            .zip(
                Try.attemptNullable { Databases.database(jooqDSLContext.dialect()) }
                    .flatMap(Try.Companion::fromOption),
                { connection: Connection, db: Database ->
                    db.apply { this.connection = connection }
                }
            )
            .map { db -> metadataGatheringDatabaseConfigurer.invoke(db) }
            .fold(
                { db -> db },
                { thr ->
                    val message: String =
                        """
                              |unable to create database instance of 
                              |[ type: ${Database::class.qualifiedName} ] 
                              |for gathering metadata and code generation 
                              |due to error [ type: ${thr::class.qualifiedName} ]
                          """.flattenIntoOneLine()
                    throw BeanCreationException(message, thr)
                }
            )
    }
}
