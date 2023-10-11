package funcify.feature.materializer.gql

import com.fasterxml.jackson.databind.ObjectMapper
import funcify.feature.json.JsonObjectMappingConfiguration
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import graphql.language.AstPrinter
import graphql.language.Document
import graphql.schema.GraphQLSchema
import graphql.schema.idl.SchemaGenerator
import kotlinx.collections.immutable.toPersistentSet
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.slf4j.Logger

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
                    |type Mutation {
                    |    addReview(review: SubmittedReview): [Review]
                    |    addArtwork(showId: Int!, upload: Upload!): [Image]! @skipcodegen
                    |}
                    |
                    |type Subscription {
                    |    reviewAdded(showId: Int!): Review
                    |}
                    |
                    |type Show {
                    |    id: Int!
                    |    title(format: TitleFormat): String!
                    |    releaseYear: Int
                    |    reviews: [Review]
                    |    artwork: [Image]
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
                    |input SubmittedReview {
                    |    showId: Int!
                    |    username: String!
                    |    starScore: Int!
                    |}
                    |
                    |type Image {
                    |    url: String
                    |}
                    |
                    |scalar DateTime
                    |scalar Upload
                    |directive @skipcodegen on FIELD_DEFINITION
                    """
                .trimMargin()
    }

    @Test
    fun createQueryTemplateTest() {
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

        logger.debug("path_set: {}", pathsSet.joinToString(",\n", "{ ", " }"))
        val document: Document =
            Assertions.assertDoesNotThrow<Document> {
                GQLOperationPathsToDocumentTransformer.invoke(graphQLSchema, pathsSet).orElseThrow()
            }
        val expectedQuery: String =
            """
            |query {
            |  shows {
            |    artwork {
            |      url
            |    }
            |    releaseYear
            |    reviews {
            |      starScore
            |      submittedDate
            |      username
            |    }
            |    title
            |  }
            |}
        """
                .trimMargin()
        Assertions.assertEquals(expectedQuery, AstPrinter.printAst(document))
    }
}
