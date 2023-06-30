package funcify.feature.datasource.graphql.metadata

import arrow.core.filterIsInstance
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.module.kotlin.treeToValue
import funcify.feature.datasource.graphql.GraphQLApiService
import funcify.feature.datasource.graphql.error.GQLDataSourceErrorResponse
import funcify.feature.datasource.graphql.error.GQLDataSourceException
import funcify.feature.datasource.graphql.metadata.provider.GraphQLApiMetadataProvider
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.MonoExtensions.widen
import funcify.feature.tools.extensions.PersistentListExtensions.reduceToPersistentList
import funcify.feature.tools.extensions.StreamExtensions.flatMapOptions
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.GraphQLError
import graphql.introspection.IntrospectionQuery
import graphql.introspection.IntrospectionResultToSchema
import graphql.language.Definition
import graphql.language.Document
import graphql.language.SDLDefinition
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import java.time.Duration
import kotlinx.collections.immutable.PersistentList
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 4/4/22
 */
class MockGraphQLApiMetadataProvider(val objectMapper: ObjectMapper) :
    GraphQLApiMetadataProvider<GraphQLApiService> {

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

        val fakeService: GraphQLApiService =
            object : GraphQLApiService {
                override val sslTlsSupported: Boolean
                    get() = true
                override val serviceName: String
                    get() = "fakeService"
                override val hostName: String
                    get() = "localhost"
                override val port: UInt
                    get() = 443u
                override val serviceContextPath: String
                    get() = "/graphql"
                override val timeoutAfter: Duration
                    get() = Duration.ofMillis(1000)

                override fun executeSingleQuery(
                    query: String,
                    variables: Map<String, Any>,
                    operationName: String?
                ): Mono<JsonNode> {
                    return Mono.just(JsonNodeFactory.instance.nullNode())
                }
            }
    }

    override fun provideTypeDefinitionRegistry(
        service: GraphQLApiService
    ): Mono<TypeDefinitionRegistry> {
        return mimicIntrospectionQueryAgainstGraphQLAPIServerOnParsedSchema()
            .flatMap { jn -> convertJsonNodeIntoTypeDefinitionRegistryInstance(jn) }
            .toMono()
            .widen()
    }

    private fun mimicIntrospectionQueryAgainstGraphQLAPIServerOnParsedSchema(): Try<JsonNode> {
        return Try.attemptNullable { SchemaParser().parse(exampleDGSSchema) }
            .flatMap(Try.Companion::fromOption)
            .map { typeDefReg: TypeDefinitionRegistry ->
                SchemaGenerator().makeExecutableSchema(typeDefReg, RuntimeWiring.MOCKED_WIRING)
            }
            .map { gs: GraphQLSchema -> GraphQL.newGraphQL(gs).build() }
            .map { gql: GraphQL -> gql.execute(IntrospectionQuery.INTROSPECTION_QUERY) }
            .filter(
                { execResult: ExecutionResult -> execResult.isDataPresent },
                { _: ExecutionResult ->
                    GQLDataSourceException(
                        GQLDataSourceErrorResponse.CLIENT_ERROR,
                        "no data is present!"
                    )
                }
            )
            .map { execResult: ExecutionResult ->
                objectMapper.valueToTree(execResult.toSpecification()) as JsonNode
            }
            .map { jn: JsonNode ->
                if (jn.has("data")) {
                    jn.get("data")
                } else {
                    objectMapper.nullNode()
                }
            }
    }

    private fun convertJsonNodeIntoTypeDefinitionRegistryInstance(
        schemaJsonNode: JsonNode
    ): Try<TypeDefinitionRegistry> {
        return Try.success(schemaJsonNode)
            .filterNot(
                { jn -> jn.isNull },
                { _: JsonNode ->
                    GQLDataSourceException(
                        GQLDataSourceErrorResponse.CLIENT_ERROR,
                        "schema_json_node is a null node"
                    )
                }
            )
            .map { jn: JsonNode -> objectMapper.treeToValue<Map<String, Any?>>(jn) }
            .map { strMap: Map<String, Any?> ->
                IntrospectionResultToSchema().createSchemaDefinition(strMap)
            }
            .map { document: Document ->
                document.definitions
                    .stream()
                    .map { def: Definition<*> ->
                        def.toOption().filterIsInstance<SDLDefinition<*>>()
                    }
                    .flatMapOptions()
                    .reduceToPersistentList()
            }
            .map { sdlDefinitions: PersistentList<SDLDefinition<*>> ->
                TypeDefinitionRegistry().apply {
                    addAll(sdlDefinitions).ifPresent { gqlerror: GraphQLError ->
                        throw GQLDataSourceException(
                            GQLDataSourceErrorResponse.Companion.GQLSpecificErrorResponse(gqlerror)
                        )
                    }
                }
            }
    }
}
