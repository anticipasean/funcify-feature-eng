package funcify.feature.materializer.gql

import ch.qos.logback.classic.Level
import com.fasterxml.jackson.databind.ObjectMapper
import funcify.feature.error.ServiceError
import funcify.feature.json.JsonObjectMappingConfiguration
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.language.AstPrinter
import graphql.language.Document
import graphql.language.OperationDefinition
import graphql.schema.GraphQLSchema
import graphql.schema.idl.SchemaGenerator
import kotlinx.collections.immutable.toPersistentSet
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class GQLOperationPathsToDocumentTransformerTest {

    companion object {
        private val logger: Logger = loggerFor<GQLOperationPathsToDocumentTransformerTest>()
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
                    |    artwork(limits: ImageLimits): [Image]
                    |}
                    |
                    |type TVShow implements Show {
                    |    id: Int!
                    |    title(format: TitleFormat): String!
                    |    releaseYear: Int
                    |    reviews(
                    |      minStarScore: Int = 0
                    |    ): [Review]
                    |    artwork(limits: ImageLimits): [Image]
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
                    |    artwork(limits: ImageLimits): [Image]
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
                lc.getLogger(GQLOperationPathsToDocumentTransformer::class.java.packageName)?.let {
                    l: ch.qos.logback.classic.Logger ->
                    l.level = Level.DEBUG
                }
            }
        }
    }

    @Test
    fun argumentWithoutDefaultNotSpecifiedForVariableDefinitionCreationTest() {
        val pathsSet =
            sequenceOf(
                    GQLOperationPath.of { field("shows", "title") },
                    GQLOperationPath.of { field("shows", "releaseYear") },
                    GQLOperationPath.of { field("shows", "reviews", "username") },
                    GQLOperationPath.of { field("shows", "reviews", "starScore") },
                    GQLOperationPath.of { field("shows", "reviews", "submittedDate") },
                    GQLOperationPath.of { field("shows", "artwork", "url") },
                    GQLOperationPath.of { field("shows", "title").argument("format") },
                    GQLOperationPath.of { field("shows").argument("titleFilter") }
                )
                .toPersistentSet()
        val graphQLSchema: GraphQLSchema =
            Assertions.assertDoesNotThrow<GraphQLSchema> {
                SchemaGenerator.createdMockedSchema(exampleDGSSchema)
            }
        val se: ServiceError =
            Assertions.assertThrows(ServiceError::class.java) {
                GQLOperationPathsToDocumentTransformer.invoke(graphQLSchema, pathsSet).orElseThrow()
            }
        Assertions.assertTrue(
            se.message.contains(
                """argument [ name: limits ] for field [ name: artwork ] 
                |has not been specified as a selected path for variable_definition creation 
                |but does not have a default value for input_type [ ImageLimits ]"""
                    .flatten()
            )
        )
    }

    @Test
    fun queryWithoutFragmentsCreationTest() {
        val pathsSet =
            sequenceOf(
                    GQLOperationPath.of { field("shows", "title") },
                    GQLOperationPath.of { field("shows", "releaseYear") },
                    GQLOperationPath.of { field("shows", "reviews", "username") },
                    GQLOperationPath.of { field("shows", "reviews", "starScore") },
                    GQLOperationPath.of { field("shows", "reviews", "submittedDate") },
                    GQLOperationPath.of { field("shows", "artwork", "url") },
                    GQLOperationPath.of { field("shows", "title").argument("format") },
                    GQLOperationPath.of { field("shows").argument("titleFilter") },
                    GQLOperationPath.of { field("shows", "artwork").argument("limits") }
                )
                .toPersistentSet()
        val graphQLSchema: GraphQLSchema =
            Assertions.assertDoesNotThrow<GraphQLSchema> {
                SchemaGenerator.createdMockedSchema(exampleDGSSchema)
            }
        val document: Document =
            Assertions.assertDoesNotThrow<Document> {
                GQLOperationPathsToDocumentTransformer.invoke(graphQLSchema, pathsSet).orElseThrow()
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
        val pathsSet =
            sequenceOf(
                    GQLOperationPath.of { field("shows", "title") },
                    GQLOperationPath.of {
                        field("shows").inlineFragment("TVShow", "numberOfSeasons")
                    },
                    GQLOperationPath.of { field("shows").inlineFragment("TVShow", "releaseYear") },
                    GQLOperationPath.of { field("shows", "reviews", "username") },
                    GQLOperationPath.of { field("shows", "reviews", "starScore") },
                    GQLOperationPath.of { field("shows", "reviews", "submittedDate") },
                    GQLOperationPath.of { field("shows", "title").argument("format") },
                    GQLOperationPath.of { field("shows").argument("titleFilter") },
                )
                .toPersistentSet()
        val graphQLSchema: GraphQLSchema =
            Assertions.assertDoesNotThrow<GraphQLSchema> {
                SchemaGenerator.createdMockedSchema(exampleDGSSchema)
            }
        val document: Document =
            Assertions.assertDoesNotThrow<Document> {
                GQLOperationPathsToDocumentTransformer.invoke(graphQLSchema, pathsSet).orElseThrow()
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
        val pathsSet =
            sequenceOf(
                    GQLOperationPath.of { field("shows", "title") },
                    GQLOperationPath.of {
                        field("shows").fragmentSpread("MyTVShowFields", "TVShow", "numberOfSeasons")
                    },
                    GQLOperationPath.of {
                        field("shows").fragmentSpread("MyTVShowFields", "TVShow", "releaseYear")
                    },
                    GQLOperationPath.of { field("shows", "reviews", "username") },
                    GQLOperationPath.of { field("shows", "reviews", "starScore") },
                    GQLOperationPath.of { field("shows", "reviews", "submittedDate") },
                    GQLOperationPath.of { field("shows", "title").argument("format") },
                    GQLOperationPath.of { field("shows").argument("titleFilter") },
                )
                .toPersistentSet()
        val graphQLSchema: GraphQLSchema =
            Assertions.assertDoesNotThrow<GraphQLSchema> {
                SchemaGenerator.createdMockedSchema(exampleDGSSchema)
            }
        val document: Document =
            Assertions.assertDoesNotThrow<Document> {
                GQLOperationPathsToDocumentTransformer.invoke(graphQLSchema, pathsSet).orElseThrow()
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
}
