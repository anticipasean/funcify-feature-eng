package funcify.feature.datasource.db.schema

import arrow.core.Eval
import arrow.core.None
import arrow.core.getOrElse
import arrow.core.toOption
import funcify.feature.tools.container.attempt.Try
import io.r2dbc.spi.ConnectionFactory
import org.jooq.ConnectionProvider
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DataSourceConnectionProvider
import org.jooq.impl.DefaultDSLContext
import org.jooq.impl.DefaultDataType
import org.jooq.meta.Database
import org.jooq.meta.Databases
import org.jooq.meta.jaxb.SchemaMappingType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.boot.r2dbc.ConnectionFactoryBuilder
import org.springframework.boot.r2dbc.EmbeddedDatabaseConnection
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.nio.file.Files
import java.nio.file.Paths


/**
 *
 * @author smccarron
 * @created 2/27/22
 */
internal class JooqSourceIndicesFactoryTest {

    companion object {

        private const val mockSchemaDDLScriptFile: String = "mock_schema_ddl.sql"

        private val mockSchemaDDLScriptResourceEval: Eval<ClassPathResource> = Eval.later { ClassPathResource(mockSchemaDDLScriptFile) }

        private lateinit var connectionFactory: ConnectionFactory

        private lateinit var context: DSLContext

        @BeforeAll
        @JvmStatic
        internal fun setUp() {

            val embeddedDatabase: EmbeddedDatabase = EmbeddedDatabaseBuilder().addScript(mockSchemaDDLScriptResourceEval.value().path)
                    .continueOnError(false)
                    .generateUniqueName(false)
                    .setName("testdb")
                    .setType(EmbeddedDatabaseType.H2)
                    .build()
            val connectionProvider: ConnectionProvider = DataSourceConnectionProvider(embeddedDatabase)
            context = DefaultDSLContext(connectionProvider,
                                        SQLDialect.H2)

        }

        internal fun r2dbcDslContext() {
            connectionFactory = ConnectionFactoryBuilder.withUrl(EmbeddedDatabaseConnection.H2.getUrl("testdb"))
                    .build()

            context = DSL.using(connectionFactory)

            Try.attempt { mockSchemaDDLScriptResourceEval.value() }
                    .map { r -> Paths.get(r.uri) }
                    .map { p -> Files.readString(p) }
                    .map { sql ->
                        Mono.from(connectionFactory.create())
                                .flatMap { c ->
                                    Mono.from(c.createStatement(sql)
                                                      .execute())
                                }
                    }
                    .orElseGet { Mono.empty() }
                    .block()

            /**
             * Currently does not succeed because {@link DSLContext} does not wire v3.16.4 #meta to r2dbc
             * connections but only jdbc ones
             */
            Assertions.assertEquals(4,
                                    context.meta())

        }
    }


    @Test
    fun testRetrievalOfCustomerRows() {
        val customerList = Flux.from(context.resultQuery("select * from customer"))
                .filter { r -> r.size() > 0 }
                .map { r -> "${r[0]} ${r[1]} ${r[2]} ${r[3]}" }
                .collectList()
                .block()
                .toOption()
                .getOrElse { emptyList() };
        Assertions.assertEquals(2,
                                customerList.size)
    }

    @Test
    fun testJooqMetaCreation() {
        val attempt = Try.attempt { context.meta().tables }
        if (attempt.isFailure()) {
            Assertions.fail<Unit>(attempt.getFailure()
                                          .orNull())
        }
        Assertions.assertEquals(1,
                                attempt.map { l -> l.asSequence() }
                                        .orElseGet { emptySequence() }
                                        .filter { t -> t.name == "CUSTOMER" }
                                        .count())
    }

    @Test
    fun createJooqRelTableTest() {
        Try.attempt<Database>({
                                  Databases.database(context.configuration()
                                                             .dialect())
                              })
                .map { d ->
                    d.apply {
                        this.connection = context.configuration()
                                .connectionProvider()
                                .acquire()
                        this.setConfiguredSchemata(listOf(SchemaMappingType().withInputSchema("PUBLIC")))
                    }
                }
                .map { d -> d.tables.toOption() }
                .orElseGet { None }
                .map { l -> l.asSequence() }
                .getOrElse { emptySequence() }
                .forEach { td ->
                    println("table_definition: ${td.name}")
                    println("-> columns: ${
                        td.columns.toOption()
                                .getOrElse { emptyList() }
                                .asSequence()
                                .map { cd ->
                                    "{ name: ${cd.name}, type: ${cd.type.type}, java_type: ${
                                        DefaultDataType.getDataType(context.dialect(),
                                                                    cd.type.type).type.name
                                    } }"
                                }
                                .joinToString(separator = ",\n",
                                              prefix = "[ ",
                                              postfix = " ]")
                    }}")
                    println("-> foreign_keys: ${
                        td.foreignKeys.asSequence()
                                .map { fk ->
                                    "{ name: ${fk.name}, key_columns: ${
                                        fk.keyColumns.asSequence()
                                                .joinToString(separator = ", ",
                                                              prefix = "[ ",
                                                              postfix = " ]") { cd -> cd.name }
                                    }, referenced_columns: ${
                                        fk.referencedColumns.asSequence()
                                                .map { cd -> "${cd.container.name}.${cd.name}" }
                                                .joinToString(separator = ", ",
                                                              prefix = "[ ",
                                                              postfix = " ]")
                                    } }"
                                }
                                .joinToString(separator = ",\n",
                                              prefix = "[ ",
                                              " ]")
                    }")
                }

    }

}