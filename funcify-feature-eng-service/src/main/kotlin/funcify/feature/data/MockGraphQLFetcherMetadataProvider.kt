package funcify.feature.data

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import funcify.feature.tools.container.async.Async
import funcify.feature.tools.container.attempt.Try
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.introspection.IntrospectionQuery
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry


/**
 *
 * @author smccarron
 * @created 4/4/22
 */
class MockGraphQLFetcherMetadataProvider(val objectMapper: ObjectMapper) : GraphQLFetcherMetadataProvider {

    companion object {
        private val exampleDGSSchema: String = """
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
                    """.trimMargin()
    }

    override fun provideMetadata(): Async<JsonNode> {
        return Async.fromAttempt(attemptToPerformIntrospectionQueryOnParsedSchema())
    }

    private fun attemptToPerformIntrospectionQueryOnParsedSchema(): Try<JsonNode> {
        return Try.attemptNullable { SchemaParser().parse(exampleDGSSchema) }
                .flatMap(Try.Companion::fromOption)
                .map { typeDefReg: TypeDefinitionRegistry ->
                    SchemaGenerator().makeExecutableSchema(typeDefReg,
                                                           RuntimeWiring.MOCKED_WIRING)
                }
                .map { gs: GraphQLSchema ->
                    GraphQL.newGraphQL(gs)
                            .build()
                }
                .map { gql: GraphQL -> gql.execute(IntrospectionQuery.INTROSPECTION_QUERY) }
                .filter({ execResult: ExecutionResult -> execResult.isDataPresent },
                        { _: ExecutionResult -> IllegalStateException("no data is present!") })
                .map { execResult: ExecutionResult -> objectMapper.valueToTree(execResult.toSpecification()) as JsonNode }
                .map { jn: JsonNode ->
                    if (jn.has("data")) {
                        jn.get("data")
                    } else {
                        JsonNodeFactory.instance.nullNode()
                    }
                }
    }

}