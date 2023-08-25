package funcify.feature.schema.directive.alias

import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.toOption
import ch.qos.logback.classic.Level
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import funcify.feature.directive.AliasDirective
import funcify.feature.directive.MaterializationDirective
import funcify.feature.error.ServiceError
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.OptionExtensions.toOption
import graphql.GraphQLError
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLSchema
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import graphql.schema.idl.UnExecutableSchemaGenerator
import kotlinx.collections.immutable.toPersistentSet
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

/**
 * @author smccarron
 * @created 2023-07-17
 */
class AliasCoordinatesRegistryTest {

    companion object {
        /**
         * Example schema obtained from
         * [DGS examples repo](https://github.com/Netflix/dgs-examples-kotlin/blob/56e7371ffad312a9d59f1318d04ab5426515e842/src/main/resources/schema/schema.graphqls)
         */
        private val exampleDGSSchema: String =
            """
                    |type Query {
                    |    shows(titleFilter: String): [Show] @alias(name: "programs")
                    |}
                    |
                    |interface Show {
                    |    id: Int! @alias(name: "showId")
                    |    title(format: TitleFormat): String!
                    |    releaseYear: Int
                    |    reviews(
                    |      minStarScore: Int = 0 @alias(name: "minimumStarScore")
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
                    |    uppercase: Boolean @alias(name: "upper")
                    |}
                    |
                    |type Review {
                    |    username: String @alias(name: "reviewerName")
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
                    """
                .trimMargin()
        @JvmStatic
        @BeforeAll
        internal fun setUp() {
            (LoggerFactory.getILoggerFactory() as? ch.qos.logback.classic.LoggerContext)?.let {
                lc: ch.qos.logback.classic.LoggerContext ->
                lc.getLogger(AttributeAliasRegistryComposer::class.java.packageName)?.let {
                    l: ch.qos.logback.classic.Logger ->
                    l.level = Level.DEBUG
                }
            }
        }
    }

    @Test
    fun createAliasCoordinatesRegistryTest() {
        val tdr: TypeDefinitionRegistry =
            Assertions.assertDoesNotThrow<TypeDefinitionRegistry> {
                SchemaParser().parse(exampleDGSSchema)
            }
        Assertions.assertDoesNotThrow<TypeDefinitionRegistry> {
            sequenceOf(AliasDirective)
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
        val aliasCoordinatesRegistry: AliasCoordinatesRegistry =
            AliasCoordinatesRegistryCreator.invoke(graphQLSchema)
        val expectedFieldAliasesJSON: String =
            """
        |[{
        |  "typeName" : "Query",
        |  "fieldName" : "shows",
        |  "aliases" : [ "programs" ]
        |}, {
        |  "typeName" : "Show",
        |  "fieldName" : "id",
        |  "aliases" : [ "showId" ]
        |}, {
        |  "typeName" : "Movie",
        |  "fieldName" : "id",
        |  "aliases" : [ "showId" ]
        |}, {
        |  "typeName" : "TVShow",
        |  "fieldName" : "id",
        |  "aliases" : [ "showId" ]
        |}, {
        |  "typeName" : "Review",
        |  "fieldName" : "username",
        |  "aliases" : [ "reviewerName" ]
        |}]
        """
                .trimMargin()
        val expectedFieldAliasesJsonNode: JsonNode =
            Assertions.assertDoesNotThrow<JsonNode> {
                ObjectMapper().readTree(expectedFieldAliasesJSON)
            }
        Assertions.assertInstanceOf(ArrayNode::class.java, expectedFieldAliasesJsonNode) {
                "not an array"
            }
            .asSequence()
            .forEach { jn: JsonNode ->
                val fc: FieldCoordinates =
                    FieldCoordinates.coordinates(
                        jn.get("typeName").asText(),
                        jn.get("fieldName").asText()
                    )
                Assertions.assertTrue(jn.get("aliases").has(0)) { "no aliases found in set" }
                Assertions.assertTrue(
                    aliasCoordinatesRegistry.isAliasForField(jn.get("aliases").get(0).asText())
                )
                Assertions.assertEquals(
                    aliasCoordinatesRegistry.getAllAliasesForField(fc),
                    jn.get("aliases").asSequence().map(JsonNode::asText).toPersistentSet()
                )
            }
        val expectedFieldArgumentAliasesJSON: String =
            """
        |[{
        |  "typeName" : "Show",
        |  "fieldName" : "reviews",
        |  "argumentName" : "minStarScore",
        |  "aliases" : [ "minimumStarScore" ]
        |}, {
        |  "typeName" : "Movie",
        |  "fieldName" : "reviews",
        |  "argumentName" : "reviews",
        |  "aliases" : [ "minimumStarScore" ]
        |}, {
        |  "typeName" : "TVShow",
        |  "fieldName" : "reviews",
        |  "argumentName" : "reviews",
        |  "aliases" : [ "minimumStarScore" ]
        |}]
        """
                .trimMargin()
        val expectedFieldArgumentAliasesJsonNode: JsonNode =
            Assertions.assertDoesNotThrow<JsonNode> {
                ObjectMapper().readTree(expectedFieldArgumentAliasesJSON)
            }
        Assertions.assertInstanceOf(ArrayNode::class.java, expectedFieldArgumentAliasesJsonNode) {
                "not an array"
            }
            .asSequence()
            .forEach { jn: JsonNode ->
                val faLoc: Pair<FieldCoordinates, String> =
                    FieldCoordinates.coordinates(
                        jn.get("typeName").asText(),
                        jn.get("fieldName").asText()
                    ) to jn.get("argumentName").asText()
                Assertions.assertTrue(jn.get("aliases").has(0)) { "no aliases found in set" }
                Assertions.assertTrue(
                    aliasCoordinatesRegistry.isAliasForFieldArgument(
                        jn.get("aliases").get(0).asText()
                    )
                )
                Assertions.assertEquals(
                    aliasCoordinatesRegistry.getAllAliasesForFieldArgument(faLoc),
                    jn.get("aliases").asSequence().map(JsonNode::asText).toPersistentSet()
                )
            }
    }
}
