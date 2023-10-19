package funcify.feature.schema.document

import ch.qos.logback.classic.Level
import com.fasterxml.jackson.databind.ObjectMapper
import funcify.feature.error.ServiceError
import funcify.feature.json.JsonObjectMappingConfiguration
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.language.AstPrinter
import graphql.language.Document
import graphql.language.OperationDefinition
import graphql.schema.GraphQLSchema
import graphql.schema.idl.SchemaGenerator
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class GQLDocumentComposerTest {

    companion object {
        private val logger: Logger = loggerFor<GQLDocumentComposerTest>()
        private val objectMapper: ObjectMapper = JsonObjectMappingConfiguration.objectMapper()
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
                    |interface Show {
                    |    id: Int!
                    |    title(format: TitleFormat): String!
                    |    releaseYear: Int
                    |    reviews(
                    |      minStarScore: Int = 0
                    |    ): [Review]
                    |    artwork(limits: ImageLimits!): [Image]
                    |}
                    |
                    |type TVShow implements Show {
                    |    id: Int!
                    |    title(format: TitleFormat): String!
                    |    releaseYear: Int
                    |    reviews(
                    |      minStarScore: Int = 0
                    |    ): [Review]
                    |    artwork(limits: ImageLimits!): [Image]
                    |    numberOfSeasons: Int
                    |}
                    |
                    |type Duration {
                    |    value: Int
                    |    unit: TimeUnit
                    |}
                    |
                    |enum TimeUnit {
                    |    SECOND
                    |    MINUTE
                    |    HOUR
                    |}
                    |
                    |type Movie implements Show {
                    |    id: Int!
                    |    title(format: TitleFormat): String!
                    |    releaseYear: Int
                    |    reviews(
                    |      minStarScore: Int = 0
                    |    ): [Review]
                    |    artwork(limits: ImageLimits!): [Image]
                    |    duration: Duration
                    |}
                    |
                    |input TitleFormat {
                    |    uppercase: Boolean
                    |}
                    |
                    |type Review {
                    |    username: String
                    |    starScore: Int
                    |    submittedDate: DateTime
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
                    |    minSize: Float = 0.0
                    |    maxSize: Float = 2.0
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
                    """
                .trimMargin()

        @JvmStatic
        @BeforeAll
        internal fun setUp() {
            (LoggerFactory.getILoggerFactory() as? ch.qos.logback.classic.LoggerContext)?.let {
                lc: ch.qos.logback.classic.LoggerContext ->
                lc.getLogger(DefaultGQLDocumentComposer::class.java.packageName)?.let {
                    l: ch.qos.logback.classic.Logger ->
                    l.level = Level.DEBUG
                }
            }
        }
    }

    private val specFactory: GQLDocumentSpecFactory = GQLDocumentSpecFactory.defaultFactory()

    @Test
    fun argumentWithoutDefaultNotSpecifiedForVariableDefinitionCreationTest() {
        val spec: GQLDocumentSpec =
            specFactory
                .builder()
                .addAllFieldPaths(
                    setOf(
                        GQLOperationPath.of { field("shows", "title") },
                        GQLOperationPath.of { field("shows", "releaseYear") },
                        GQLOperationPath.of { field("shows", "reviews", "username") },
                        GQLOperationPath.of { field("shows", "reviews", "starScore") },
                        GQLOperationPath.of { field("shows", "reviews", "submittedDate") },
                        GQLOperationPath.of { field("shows", "artwork", "url") },
                    )
                )
                .putAllArgumentPathsForVariableNames(
                    setOf(
                        "format" to
                            GQLOperationPath.of { field("shows", "title").argument("format") },
                        "titleFilter" to
                            GQLOperationPath.of { field("shows").argument("titleFilter") }
                    )
                )
                .build()
        val graphQLSchema: GraphQLSchema =
            Assertions.assertDoesNotThrow<GraphQLSchema> {
                SchemaGenerator.createdMockedSchema(exampleDGSSchema)
            }
        val docCreationAttempt: Try<Document> =
            DefaultGQLDocumentComposer.composeDocumentFromSpecWithSchema(spec, graphQLSchema)
        val se: ServiceError =
            Assertions.assertThrows(ServiceError::class.java) { docCreationAttempt.orElseThrow() }
        Assertions.assertTrue(
            se.message.contains(
                """does not have an assigned variable_name, 
                    |an assigned default GraphQL value, 
                    |nor a schema-defined default GraphQL value 
                    |for non-nullable input_type"""
                    .flatten()
            )
        ) {
            "message does not match expected pattern: [ actual_message: %s ]".format(se.message)
        }
    }

    @Test
    fun queryWithoutFragmentsCreationTest() {
        val spec: GQLDocumentSpec =
            specFactory
                .builder()
                .addAllFieldPaths(
                    setOf(
                        GQLOperationPath.of { field("shows", "title") },
                        GQLOperationPath.of { field("shows", "releaseYear") },
                        GQLOperationPath.of { field("shows", "reviews", "username") },
                        GQLOperationPath.of { field("shows", "reviews", "starScore") },
                        GQLOperationPath.of { field("shows", "reviews", "submittedDate") },
                        GQLOperationPath.of { field("shows", "artwork", "url") }
                    )
                )
                .putAllArgumentPathsForVariableNames(
                    setOf(
                        "format" to
                            GQLOperationPath.of { field("shows", "title").argument("format") },
                        "titleFilter" to
                            GQLOperationPath.of { field("shows").argument("titleFilter") },
                        "limits" to
                            GQLOperationPath.of { field("shows", "artwork").argument("limits") }
                    )
                )
                .build()
        val graphQLSchema: GraphQLSchema =
            Assertions.assertDoesNotThrow<GraphQLSchema> {
                SchemaGenerator.createdMockedSchema(exampleDGSSchema)
            }
        val document: Document =
            Assertions.assertDoesNotThrow<Document> {
                DefaultGQLDocumentComposer.composeDocumentFromSpecWithSchema(spec, graphQLSchema)
                    .orElseThrow()
            }
        Assertions.assertTrue(
            document.getDefinitionsOfType(OperationDefinition::class.java).isNotEmpty()
        ) {
            "document does not contain any operation_definition"
        }
        val expectedOutputFormat: String =
            """
            |query (${"$"}titleFilter: String, ${"$"}format: TitleFormat, ${"$"}limits: ImageLimits) {
            |  shows(titleFilter: ${"$"}titleFilter) {
            |    title(format: ${"$"}format)
            |    releaseYear
            |    reviews(minStarScore: 0) {
            |      username
            |      starScore
            |      submittedDate
            |    }
            |    artwork(limits: ${"$"}limits) {
            |      url
            |    }
            |  }
            |}
            |
        """
                .trimMargin()
        Assertions.assertEquals(expectedOutputFormat, AstPrinter.printAst(document)) {
            "document does not match expected output format"
        }
    }

    @Test
    fun queryWithInlineFragmentCreationTest() {
        val spec: GQLDocumentSpec =
            specFactory
                .builder()
                .addAllFieldPaths(
                    setOf(
                        GQLOperationPath.of { field("shows", "title") },
                        GQLOperationPath.of {
                            field("shows").inlineFragment("TVShow", "numberOfSeasons")
                        },
                        GQLOperationPath.of {
                            field("shows").inlineFragment("TVShow", "releaseYear")
                        },
                        GQLOperationPath.of { field("shows", "reviews", "username") },
                        GQLOperationPath.of { field("shows", "reviews", "starScore") },
                        GQLOperationPath.of { field("shows", "reviews", "submittedDate") },
                    )
                )
                .putAllArgumentPathsForVariableNames(
                    setOf(
                        "format" to
                            GQLOperationPath.of { field("shows", "title").argument("format") },
                        "titleFilter" to
                            GQLOperationPath.of { field("shows").argument("titleFilter") }
                    )
                )
                .build()
        val graphQLSchema: GraphQLSchema =
            Assertions.assertDoesNotThrow<GraphQLSchema> {
                SchemaGenerator.createdMockedSchema(exampleDGSSchema)
            }
        val document: Document =
            Assertions.assertDoesNotThrow<Document> {
                DefaultGQLDocumentComposer.composeDocumentFromSpecWithSchema(spec, graphQLSchema)
                    .orElseThrow()
            }
        Assertions.assertTrue(
            document.getDefinitionsOfType(OperationDefinition::class.java).isNotEmpty()
        ) {
            "document does not contain any operation_definition"
        }
        val expectedOutputFormat: String =
            """
            |query (${"$"}titleFilter: String, ${"$"}format: TitleFormat) {
            |  shows(titleFilter: ${"$"}titleFilter) {
            |    title(format: ${"$"}format)
            |    reviews(minStarScore: 0) {
            |      username
            |      starScore
            |      submittedDate
            |    }
            |    ... on TVShow {
            |      numberOfSeasons
            |      releaseYear
            |    }
            |  }
            |}
            |
            """
                .trimMargin()
        Assertions.assertEquals(expectedOutputFormat, AstPrinter.printAst(document)) {
            "document does not match expected output format"
        }
    }

    @Test
    fun queryWithFragmentSpreadCreationTest() {
        val spec: GQLDocumentSpec =
            specFactory
                .builder()
                .addAllFieldPaths(
                    setOf(
                        GQLOperationPath.of { field("shows", "title") },
                        GQLOperationPath.of {
                            field("shows")
                                .fragmentSpread("MyTVShowFields", "TVShow", "numberOfSeasons")
                        },
                        GQLOperationPath.of {
                            field("shows").fragmentSpread("MyTVShowFields", "TVShow", "releaseYear")
                        },
                        GQLOperationPath.of { field("shows", "reviews", "username") },
                        GQLOperationPath.of { field("shows", "reviews", "starScore") },
                        GQLOperationPath.of { field("shows", "reviews", "submittedDate") },
                    )
                )
                .putAllArgumentPathsForVariableNames(
                    setOf(
                        "format" to
                            GQLOperationPath.of { field("shows", "title").argument("format") },
                        "titleFilter" to
                            GQLOperationPath.of { field("shows").argument("titleFilter") }
                    )
                )
                .build()
        val graphQLSchema: GraphQLSchema =
            Assertions.assertDoesNotThrow<GraphQLSchema> {
                SchemaGenerator.createdMockedSchema(exampleDGSSchema)
            }
        val document: Document =
            Assertions.assertDoesNotThrow<Document> {
                DefaultGQLDocumentComposer.composeDocumentFromSpecWithSchema(spec, graphQLSchema)
                    .orElseThrow()
            }
        Assertions.assertTrue(
            document.getDefinitionsOfType(OperationDefinition::class.java).isNotEmpty()
        ) {
            "document does not contain any operation_definition"
        }
        val expectedOutputFormat: String =
            """
            |query (${"$"}titleFilter: String, ${"$"}format: TitleFormat) {
            |  shows(titleFilter: ${"$"}titleFilter) {
            |    title(format: ${"$"}format)
            |    reviews(minStarScore: 0) {
            |      username
            |      starScore
            |      submittedDate
            |    }
            |    ...MyTVShowFields
            |  }
            |}
            |
            |fragment MyTVShowFields on TVShow {
            |  numberOfSeasons
            |  releaseYear
            |}
            |
            """
                .trimMargin()
        Assertions.assertEquals(expectedOutputFormat, AstPrinter.printAst(document)) {
            "document does not match expected output format"
        }
    }

    @Test
    fun queryWithAliasesWithoutFragmentsCreationTest() {
        val spec: GQLDocumentSpec =
            specFactory
                .builder()
                .addAllFieldPaths(
                    setOf(
                        GQLOperationPath.of { field("shows").aliasedField("name", "title") },
                        GQLOperationPath.of { field("shows", "releaseYear") },
                        GQLOperationPath.of { field("shows", "reviews", "username") },
                        GQLOperationPath.of { field("shows", "reviews", "starScore") },
                        GQLOperationPath.of { field("shows", "reviews", "submittedDate") },
                        GQLOperationPath.of { field("shows", "artwork", "url") }
                    )
                )
                .putAllArgumentPathsForVariableNames(
                    setOf(
                        "format" to
                            GQLOperationPath.of {
                                field("shows").aliasedField("name", "title").argument("format")
                            },
                        "titleFilter" to
                            GQLOperationPath.of { field("shows").argument("titleFilter") },
                        "limits" to
                            GQLOperationPath.of { field("shows", "artwork").argument("limits") }
                    )
                )
                .build()
        val graphQLSchema: GraphQLSchema =
            Assertions.assertDoesNotThrow<GraphQLSchema> {
                SchemaGenerator.createdMockedSchema(exampleDGSSchema)
            }
        val document: Document =
            Assertions.assertDoesNotThrow<Document> {
                DefaultGQLDocumentComposer.composeDocumentFromSpecWithSchema(spec, graphQLSchema)
                    .orElseThrow()
            }
        Assertions.assertTrue(
            document.getDefinitionsOfType(OperationDefinition::class.java).isNotEmpty()
        ) {
            "document does not contain any operation_definition"
        }
        val expectedOutputFormat: String =
            """
            |query (${"$"}titleFilter: String, ${"$"}format: TitleFormat, ${"$"}limits: ImageLimits) {
            |  shows(titleFilter: ${"$"}titleFilter) {
            |    name: title(format: ${"$"}format)
            |    releaseYear
            |    reviews(minStarScore: 0) {
            |      username
            |      starScore
            |      submittedDate
            |    }
            |    artwork(limits: ${"$"}limits) {
            |      url
            |    }
            |  }
            |}
            |
            """
                .trimMargin()
        Assertions.assertEquals(expectedOutputFormat, AstPrinter.printAst(document)) {
            "document does not match expected output format"
        }
    }
}
