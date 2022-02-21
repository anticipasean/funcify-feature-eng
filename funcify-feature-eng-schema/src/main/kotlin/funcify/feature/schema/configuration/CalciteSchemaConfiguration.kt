package funcify.feature.schema.configuration

import arrow.core.toOption
import com.google.common.collect.ImmutableList
import funcify.feature.tools.container.attempt.Try
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.apache.calcite.plan.Contexts
import org.apache.calcite.plan.RelOptCostImpl
import org.apache.calcite.rel.type.RelDataTypeSystem
import org.apache.calcite.rex.RexUtil
import org.apache.calcite.schema.SchemaPlus
import org.apache.calcite.sql.SqlDialect
import org.apache.calcite.sql.SqlDialectFactoryImpl
import org.apache.calcite.sql.`fun`.SqlStdOperatorTable
import org.apache.calcite.sql.parser.SqlParser
import org.apache.calcite.sql.validate.SqlValidator
import org.apache.calcite.sql2rel.SqlToRelConverter
import org.apache.calcite.sql2rel.StandardConvertletTable
import org.apache.calcite.statistic.QuerySqlStatisticProvider
import org.apache.calcite.tools.FrameworkConfig
import org.apache.calcite.tools.Frameworks
import org.apache.calcite.tools.Programs
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource


/**
 *
 * @author smccarron
 * @created 2/20/22
 */
@Configuration
class CalciteSchemaConfiguration {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(CalciteSchemaConfiguration::class.java)
        private const val ADD_METADATA_SCHEMA_FLAG = true
    }

    @Bean
    fun rootCalciteSchema(): SchemaPlus {
        return Frameworks.createRootSchema(ADD_METADATA_SCHEMA_FLAG)
    }

    @Bean
    fun sqlToRelConverterConfig(): SqlToRelConverter.Config {
        return SqlToRelConverter.config()
                .withTrimUnusedFields(false)
    }

    @Bean
    fun calciteFrameworkConfig(datasourceProvider: ObjectProvider<DataSource>,
                               rootCalciteSchema: SchemaPlus,
                               sqlToRelConverterConfig: SqlToRelConverter.Config): FrameworkConfig {
        /**
         * TODO: Add support for sql dialect injection but keep default until datasource types can
         * be assessed
         */
        return Frameworks.newConfigBuilder()
                .context(Contexts.empty())
                .defaultSchema(rootCalciteSchema)
                .parserConfig(SqlParser.Config.DEFAULT)
                .costFactory(RelOptCostImpl.FACTORY)
                .convertletTable(StandardConvertletTable.INSTANCE)
                .evolveLattice(false)
                .executor(RexUtil.EXECUTOR)
                .operatorTable(SqlStdOperatorTable.instance())
                .programs(Programs.ofRules(Programs.RULE_SET),
                          Programs.CALC_PROGRAM
                         )
                .sqlToRelConverterConfig(sqlToRelConverterConfig)
                .sqlValidatorConfig(SqlValidator.Config.DEFAULT)
                .statisticProvider(QuerySqlStatisticProvider.SILENT_CACHING_INSTANCE)
                .traitDefs(ImmutableList.of())
                .typeSystem(RelDataTypeSystem.DEFAULT)
                .build()
    }

    private fun extractCalciteSupportedSqlDialectsFromProvidedDataSources(datasourceProvider: ObjectProvider<DataSource>): PersistentMap<DataSource, SqlDialect> {
        return Try.attempt({
                               datasourceProvider.asSequence()
                                       .flatMap { ds ->
                                           ds.toOption()
                                                   .fold({ emptySequence() },
                                                         { d -> sequenceOf(d) })
                                       }
                           })
                .map { dsSeq ->
                    dsSeq.fold(persistentMapOf<DataSource, SqlDialect>(),
                               { acc: PersistentMap<DataSource, SqlDialect>, datasource: DataSource ->
                                   SqlDialectFactoryImpl.INSTANCE.create(datasource.connection.metaData)
                                           .toOption()
                                           .fold({ acc },
                                                 { sd ->
                                                     acc.put(datasource,
                                                             sd
                                                            )
                                                 })
                               })
                }
                .orElseGet { persistentMapOf() }
    }

}