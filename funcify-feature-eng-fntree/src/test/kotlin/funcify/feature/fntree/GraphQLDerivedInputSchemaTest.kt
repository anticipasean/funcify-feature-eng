package funcify.feature.fntree

import arrow.core.filterIsInstance
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.treeToValue
import funcify.feature.json.JsonObjectMappingConfiguration
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.PersistentListExtensions.reduceToPersistentList
import funcify.feature.tools.extensions.StreamExtensions.flatMapOptions
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.GraphQLError
import graphql.introspection.IntrospectionQueryBuilder
import graphql.introspection.IntrospectionResultToSchema
import graphql.language.Definition
import graphql.language.Document
import graphql.language.FieldDefinition
import graphql.language.SDLDefinition
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import kotlinx.collections.immutable.PersistentList
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * @author smccarron
 * @created 4/4/22
 */
class GraphQLDerivedInputSchemaTest {

    companion object {
        /**
         * Example schema obtained from
         * [DGS examples repo](https://github.com/Netflix/dgs-examples-kotlin/blob/56e7371ffad312a9d59f1318d04ab5426515e842/src/main/resources/schema/schema.graphqls)
         */
        private val exampleDGSSchema: String =
            """
        |type Query {
        |    shows(filter: [String!] = []): [Show]
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
    fun createGraphQLSchemaTest() {
        val objectMapper: ObjectMapper = JsonObjectMappingConfiguration.objectMapper()
        val schemaJsonNode: JsonNode =
            Assertions.assertDoesNotThrow<JsonNode> {
                mimicIntrospectionQueryAgainstGraphQLAPIServerOnParsedSchema(objectMapper)
                    .orElseThrow()
            }
        val graphQLSchema: GraphQLSchema =
            Assertions.assertDoesNotThrow<GraphQLSchema> {
                convertJsonNodeIntoGraphQLSchemaInstance(schemaJsonNode, objectMapper).orElseThrow()
            }
        //println(
        //    graphQLSchema.getObjectType("Mutation").definition?.fieldDefinitions?.firstOrNull {
        //        fd: FieldDefinition ->
        //        fd.name == "addArtwork"
        //    }
        //)
    }

    private fun mimicIntrospectionQueryAgainstGraphQLAPIServerOnParsedSchema(
        objectMapper: ObjectMapper
    ): Try<JsonNode> {
        return Try.attemptNullable { SchemaParser().parse(exampleDGSSchema) }
            .flatMap(Try.Companion::fromOption)
            .map { typeDefReg: TypeDefinitionRegistry ->
                SchemaGenerator().makeExecutableSchema(typeDefReg, RuntimeWiring.MOCKED_WIRING)
            }
            .map { gs: GraphQLSchema -> GraphQL.newGraphQL(gs).build() }
            .map { gql: GraphQL ->
                gql.execute(
                    IntrospectionQueryBuilder.build(
                        IntrospectionQueryBuilder.Options.defaultOptions().descriptions(true)
                    )
                )
            }
            .filter({ er: ExecutionResult -> er.isDataPresent }) { _: ExecutionResult ->
                RuntimeException("no data is present!")
            }
            .map { execResult: ExecutionResult ->
                objectMapper.valueToTree<JsonNode>(execResult.toSpecification())
            }
            .map { jn: JsonNode ->
                if (jn.has("data")) {
                    jn.get("data")
                } else {
                    objectMapper.nullNode()
                }
            }
    }

    private fun convertJsonNodeIntoGraphQLSchemaInstance(
        schemaJsonNode: JsonNode,
        objectMapper: ObjectMapper
    ): Try<GraphQLSchema> {
        return Try.success(schemaJsonNode)
            .filterNot({ jn: JsonNode -> jn.isNull }) { _: JsonNode ->
                RuntimeException("json_node is null_node")
            }
            .map { jn: JsonNode -> objectMapper.treeToValue<Map<String, Any?>>(jn) }
            .map { m: Map<String, Any?> -> IntrospectionResultToSchema().createSchemaDefinition(m) }
            .map { d: Document ->
                d.definitions
                    .stream()
                    .map { def: Definition<*> ->
                        def.toOption().filterIsInstance<SDLDefinition<*>>()
                    }
                    .flatMapOptions()
                    .reduceToPersistentList()
            }
            .map { sdls: PersistentList<SDLDefinition<*>> ->
                TypeDefinitionRegistry().apply {
                    addAll(sdls).ifPresent { gqlerror: GraphQLError ->
                        throw RuntimeException("graphql_error: %s".format(gqlerror))
                    }
                }
            }
            .map { tdr: TypeDefinitionRegistry ->
                SchemaGenerator().makeExecutableSchema(tdr, RuntimeWiring.MOCKED_WIRING)
            }
    }
}
