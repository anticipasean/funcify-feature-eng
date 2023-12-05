package funcify.feature.schema.directive.temporal

import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.toOption
import ch.qos.logback.classic.Level
import funcify.feature.directive.AliasDirective
import funcify.feature.directive.LastUpdatedDirective
import funcify.feature.directive.MaterializationDirective
import funcify.feature.error.ServiceError
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.OptionExtensions.toOption
import graphql.GraphQLError
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLSchema
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import graphql.schema.idl.UnExecutableSchemaGenerator
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

/**
 * @author smccarron
 * @created 2023-07-19
 */
class LastUpdatedCoordinatesRegistryTest {

    companion object {
        /**
         * Example schema obtained from
         * [DGS examples repo](https://github.com/Netflix/dgs-examples-kotlin/blob/56e7371ffad312a9d59f1318d04ab5426515e842/src/main/resources/schema/schema.graphqls)
         */
        private val exampleDGSSchema: String =
            """
            |type Query {
            |    shows(titleFilter: String): [Show]
            |}
            |
            |type Show {
            |    id: Int!
            |    title(format: TitleFormat): String!
            |    releaseYear: Int
            |    reviews(
            |      minStarScore: Int = 0
            |    ): [Review]
            |    artwork(limits: ImageLimits): [Image]
            |    added: Date @lastUpdated
            |}
            |
            |input TitleFormat {
            |    uppercase: Boolean @alias(name: "upper")
            |}
            |
            |type Review {
            |    username: String
            |    starScore: Int
            |    submittedDate: DateTime @lastUpdated
            |}
            |
            |type Image {
            |    url: String
            |}
            |
            |input ImageLimits {
            |    fileLimits: FileLimits = { maxSize: 4.0 }
            |    imageNames: [String!]! = []
            |    includeNames: String = ".*"
            |    excludeNames: String = ""
            |}
            |
            |input FileLimits {
            |    minSize: Float = 0.0 @alias(name: "minimumSize")
            |    maxSize: Float = 2.0 @alias(name: "maximumSize")
            |    unit: SizeUnit = MB  
            |}
            |
            |enum SizeUnit {
            |    GB
            |    MB
            |    KB
            |}
            |
            |scalar DateTime
            |scalar Date
            """
                .trimMargin()

        @JvmStatic
        @BeforeAll
        internal fun setUp() {
            (LoggerFactory.getILoggerFactory() as? ch.qos.logback.classic.LoggerContext)?.let {
                lc: ch.qos.logback.classic.LoggerContext ->
                lc.getLogger(LastUpdatedCoordinatesRegistry::class.java.packageName)?.let {
                    l: ch.qos.logback.classic.Logger ->
                    l.level = Level.DEBUG
                }
            }
        }
    }

    @Test
    fun createLastUpdatedTemporalAttributeRegistryTest() {
        val tdr: TypeDefinitionRegistry =
            Assertions.assertDoesNotThrow<TypeDefinitionRegistry> {
                SchemaParser().parse(exampleDGSSchema)
            }
        Assertions.assertDoesNotThrow<TypeDefinitionRegistry> {
            sequenceOf(AliasDirective, LastUpdatedDirective)
                .fold(Try.success(tdr)) {
                    t: Try<TypeDefinitionRegistry>,
                    d: MaterializationDirective ->
                    t.flatMap { reg: TypeDefinitionRegistry ->
                        reg.add(d.directiveDefinition)
                            .toOption()
                            .fold(
                                { Try.success(reg) },
                                { e: GraphQLError ->
                                    Try.failure<TypeDefinitionRegistry>(
                                        e.toOption().filterIsInstance<Throwable>().getOrElse {
                                            ServiceError.of(
                                                "graphql_error: %s",
                                                e.toSpecification()
                                            )
                                        }
                                    )
                                }
                            )
                    }
                }
                .orElseThrow()
        }
        val graphQLSchema: GraphQLSchema =
            Assertions.assertDoesNotThrow<GraphQLSchema> {
                UnExecutableSchemaGenerator.makeUnExecutableSchema(tdr)
            }
        val lastUpdatedRegistry: LastUpdatedCoordinatesRegistry =
            Assertions.assertDoesNotThrow<LastUpdatedCoordinatesRegistry> {
                LastUpdatedCoordinatesRegistryCreator.createLastUpdatedCoordinatesRegistryFor(
                        graphQLSchema,
                        GQLOperationPath.parseOrThrow("gqlo:/shows"),
                        FieldCoordinates.coordinates("Query", "shows")
                    )
                    .orElseThrow()
            }
        //println("registry: %s".format(lastUpdatedRegistry))
        Assertions.assertTrue(
            lastUpdatedRegistry.pathBelongsToLastUpdatedField(
                GQLOperationPath.parseOrThrow("gqlo:/shows/reviews/submittedDate")
            )
        )
        Assertions.assertTrue(
            lastUpdatedRegistry.pathBelongsToParentOfLastUpdatedField(
                GQLOperationPath.parseOrThrow("gqlo:/shows/reviews")
            )
        )
        Assertions.assertTrue(
            lastUpdatedRegistry.pathBelongsToLastUpdatedField(
                GQLOperationPath.parseOrThrow("gqlo:/shows/added")
            )
        )
        Assertions.assertTrue(
            lastUpdatedRegistry.pathBelongsToParentOfLastUpdatedField(
                GQLOperationPath.parseOrThrow("gqlo:/shows")
            )
        )
    }
}
