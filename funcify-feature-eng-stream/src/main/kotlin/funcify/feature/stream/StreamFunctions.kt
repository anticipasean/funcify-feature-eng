package funcify.feature.stream

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import funcify.feature.datasource.graphql.GraphQLApiDataElementSourceProvider
import funcify.feature.datasource.graphql.GraphQLApiDataElementSourceProviderFactory
import funcify.feature.materializer.schema.MaterializationMetamodel
import funcify.feature.materializer.schema.MaterializationMetamodelBroker
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.schema.GraphQLSchema
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.core.io.ClassPathResource
import org.springframework.messaging.Message
import org.springframework.messaging.support.GenericMessage
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-06-20
 */
@SpringBootApplication(scanBasePackages = ["funcify.feature"])
class StreamFunctions {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(StreamFunctions::class.java)
        private val objectMapper: ObjectMapper = ObjectMapper()
        val QUERY: String =
            """
                |query showFeatures(${'$'}show_id: ID!){
                |    dataElement {
                |        show(showId: ${'$'}show_id) {
                |            showId
                |            title
                |            releaseYear
                |            director {
                |                name
                |            }
                |            cast {
                |                name
                |            }
                |            audienceSuitabilityRating
                |            productionCountry
                |            genres
                |            dateAdded
                |        }
                |    }
                |}
            """
                .trimMargin()

        @JvmStatic
        fun main(args: Array<String>) {
            SpringApplication.run(StreamFunctions::class.java, *args)
        }
    }

    @Bean(name = ["graphQLSchemaFile"])
    fun graphQLSchemaFile(): ClassPathResource {
        return ClassPathResource("netflix_movies_and_tv_shows.graphqls")
    }

    @Bean(name = ["netflixShowsGraphQLDataElementSourceProvider"])
    fun netflixShowsGraphQLDataElementSourceProvider(
        graphQLSchemaFile: ClassPathResource,
        graphQLApiDataElementSourceProviderFactory: GraphQLApiDataElementSourceProviderFactory
    ): GraphQLApiDataElementSourceProvider {
        return graphQLApiDataElementSourceProviderFactory
            .builder()
            .name("netflix_shows")
            .graphQLSchemaClasspathResource(graphQLSchemaFile)
            .build()
            .peek(
                { p: GraphQLApiDataElementSourceProvider ->
                    logger.info(
                        "netflixShowsGraphQLDataElementSourceProvider: [ status: success ][ name: {} ]",
                        p.name
                    )
                },
                { t: Throwable ->
                    logger.error(
                        "netflixShowsGraphQLDataElementSourceProvider: [ status: failure ][ type: {}, message: {} ]",
                        t::class.qualifiedName,
                        t.message
                    )
                }
            )
            .orElseThrow()
    }

    /*@Bean(name = ["netflixShowsGraphQLSchema"])
    fun netflixShowsGraphQLSchema(
        @Qualifier("graphQLSchemaFile") graphQLSchemaFile: ClassPathResource
    ): GraphQLSchema {
        return NetflixShowsGraphQL.createGraphQLSchemaFromFile(graphQLSchemaFile.file)
    }*/

    @Bean
    fun materializeFeatures(
        metamodelBroker: MaterializationMetamodelBroker
    ): (Flux<Message<Any?>>) -> Flux<Message<JsonNode>> {
        return { messagePublisher: Flux<Message<Any?>> ->
            messagePublisher
                .zipWith(metamodelBroker.fetchLatestMaterializationMetamodel(), ::Pair)
                .flatMap { (m: Message<Any?>, mm: MaterializationMetamodel) ->
                    logger.info("headers: {}", m.headers)
                    when (val pl: Any? = m.payload) {
                            null -> {
                                // TODO: Instate null node treatment here: Mono.just(nullNode)
                                // or Mono.empty<JsonNode>()
                                Mono.just(JsonNodeFactory.instance.nullNode())
                            }
                            is ByteArray -> {
                                Mono.fromSupplier {
                                    try {
                                        // TODO: Instate null node treatment here:
                                        // Mono.just(nullNode)
                                        // or Mono.empty<JsonNode>()
                                        objectMapper.readTree(pl)
                                            ?: JsonNodeFactory.instance.nullNode()
                                    } catch (t: Throwable) {
                                        JsonNodeFactory.instance
                                            .objectNode()
                                            .put("errorType", t::class.qualifiedName)
                                            .put("errorMessage", t.message)
                                    }
                                }
                            }
                            is String -> {
                                Mono.just(JsonNodeFactory.instance.textNode(pl))
                            }
                            is JsonNode -> {
                                Mono.just(pl)
                            }
                            else -> {
                                Mono.fromSupplier {
                                    val supportedPayloadTypes: String =
                                        sequenceOf(
                                                ByteArray::class.qualifiedName,
                                                String::class.qualifiedName,
                                                JsonNode::class.qualifiedName
                                            )
                                            .joinToString(", ", "[ ", " ]")
                                    val message: String =
                                        """unsupported message payload type: 
                                    |[ expected: one of %s, actual: %s ]"""
                                            .trimMargin()
                                            .replace(System.lineSeparator(), "")
                                            .format(supportedPayloadTypes, pl::class.qualifiedName)
                                    JsonNodeFactory.instance
                                        .objectNode()
                                        .put(
                                            "errorType",
                                            IllegalArgumentException::class.qualifiedName
                                        )
                                        .put("errorMessage", message)
                                }
                            }
                        }
                        .flatMap { jn: JsonNode ->
                            executeGraphQLRequestWithSchema(jn, mm.materializationGraphQLSchema)
                        }
                        .map { jn: JsonNode -> GenericMessage<JsonNode>(jn, m.headers) }
                }
        }
    }

    private fun executeGraphQLRequestWithSchema(
        requestBodyJson: JsonNode,
        netflixShowsGraphQLSchema: GraphQLSchema
    ): Mono<JsonNode> {
        logger.info(
            "execute_graphql_request_with_schema: [ request_body_json: {} ]",
            requestBodyJson
        )
        return Mono.fromFuture(
                GraphQL.newGraphQL(netflixShowsGraphQLSchema)
                    .build()
                    .executeAsync(
                        ExecutionInput.newExecutionInput()
                            .query(QUERY)
                            .variables(
                                mapOf(
                                    "show_id" to requestBodyJson.get("show_id"),
                                    "somethingElse" to
                                        JsonNodeFactory.instance
                                            .objectNode()
                                            .put("field1", "some_value")
                                )
                            )
                            // can place as root here or can inject in GraphQLContext
                            // TODO: Consider creating UnprocessedCSVJSON type and accompanying
                            // types
                            .root(requestBodyJson)
                    )
            )
            .map { er: ExecutionResult ->
                try {
                    objectMapper.valueToTree<JsonNode>(er.toSpecification())
                } catch (t: Throwable) {
                    JsonNodeFactory.instance
                        .objectNode()
                        .put("errorType", t::class.qualifiedName)
                        .put("errorMessage", t.message)
                }
            }
    }
}
