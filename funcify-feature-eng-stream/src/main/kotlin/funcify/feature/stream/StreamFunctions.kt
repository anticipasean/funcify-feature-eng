package funcify.feature.stream

import arrow.core.filterIsInstance
import arrow.core.foldLeft
import arrow.core.getOrElse
import arrow.core.identity
import arrow.core.orElse
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import funcify.feature.datasource.graphql.GraphQLApiDataElementSourceProvider
import funcify.feature.datasource.graphql.GraphQLApiDataElementSourceProviderFactory
import funcify.feature.error.ServiceError
import funcify.feature.file.FileRegistryFeatureCalculatorProvider
import funcify.feature.file.FileRegistryFeatureCalculatorProviderFactory
import funcify.feature.materializer.executor.GraphQLSingleRequestExecutor
import funcify.feature.materializer.input.context.RawInputContext
import funcify.feature.materializer.request.RawGraphQLRequest
import funcify.feature.materializer.request.factory.RawGraphQLRequestFactory
import funcify.feature.materializer.response.SerializedGraphQLResponse
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.transformer.jq.JqTransformerSourceProvider
import funcify.feature.transformer.jq.JqTransformerSourceProviderFactory
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.introspection.IntrospectionQuery
import graphql.schema.GraphQLSchema
import java.util.concurrent.CompletableFuture
import org.dataloader.BatchLoaderEnvironment
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderOptions
import org.dataloader.DataLoaderRegistry
import org.dataloader.MappedBatchLoaderWithContext
import org.dataloader.stats.SimpleStatisticsCollector
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
        const val OPERATION_NAME: String = ""
        val QUERY: String =
            """
                |query ${OPERATION_NAME}(${'$'}show_id: ID!){
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
                |            ...TVShowFragment
                |            ...MovieFragment
                |        }
                |    }
                |    transformer {
                |        jq {
                |            negative_to_null(input: -1)
                |        }
                |    }
                |    features {
                |        showFeatures {
                |            castCount
                |            releaseDecade
                |        }
                |    }
                |}
                |fragment TVShowFragment on TVShow {
                |    numberOfSeasons
                |    genres
                |}
                |fragment MovieFragment on Movie {
                |    duration
                |    productionCountry
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

    @Bean(name = ["jqTransformersFile"])
    fun jqTransformersFile(): ClassPathResource {
        return ClassPathResource("jq-transformers.yml")
    }

    @Bean(name = ["jqTransformersSourceProvider"])
    fun jqTransformersSourceProvider(
        jqTransformersFile: ClassPathResource,
        jqTransformerSourceProviderFactory: JqTransformerSourceProviderFactory
    ): JqTransformerSourceProvider {
        return jqTransformerSourceProviderFactory
            .builder()
            .name("jq")
            .transformerYamlFile(jqTransformersFile)
            .build()
            .orElseThrow()
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

    @Bean(name = ["movieAndTvShowFeaturesFile"])
    fun movieAndTvShowFeaturesFile(): ClassPathResource {
        return ClassPathResource("movie_and_tv_show_features.graphqls")
    }

    @Bean(name = ["movieAndTvShowFeaturesFileRegistryFeatureCalculatorProvider"])
    fun movieAndTvShowFeaturesFileRegistryFeatureCalculatorProvider(
        movieAndTvShowFeaturesFile: ClassPathResource,
        fileRegistryFeatureCalculatorProviderFactory: FileRegistryFeatureCalculatorProviderFactory,
    ): FileRegistryFeatureCalculatorProvider {
        return fileRegistryFeatureCalculatorProviderFactory
            .builder()
            .name("movieAndTvShowFeatures")
            .graphQLSchemaClasspathResource(movieAndTvShowFeaturesFile)
            .build()
            .orElseThrow()
    }

    @Bean
    fun materializeFeatures(
        rawGraphQLRequestFactory: RawGraphQLRequestFactory,
        graphQLSingleRequestExecutor: GraphQLSingleRequestExecutor
    ): (Flux<Message<Any?>>) -> Flux<Message<JsonNode>> {
        return { messagePublisher: Flux<Message<Any?>> ->
            messagePublisher.flatMap { m: Message<Any?> ->
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
                                    objectMapper.readTree(pl) ?: JsonNodeFactory.instance.nullNode()
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
                            Mono.error<JsonNode> {
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
                                IllegalArgumentException(message)
                            }
                        }
                    }
                    .map { jn: JsonNode ->
                        rawGraphQLRequestFactory
                            .builder()
                            .operationName(OPERATION_NAME)
                            .headers(m.headers)
                            .rawGraphQLQueryText(QUERY)
                            .variable(
                                RawInputContext.RAW_INPUT_CONTEXT_VARIABLE_KEY,
                                convertJsonNodeIntoSchemaFormatJsonNode(jn)
                            )
                            .variable("show_id", jn.get("show_id"))
                            .build()
                    }
                    .flatMap { rr: RawGraphQLRequest ->
                        graphQLSingleRequestExecutor
                            .executeSingleRequest(rr)
                            .map { sgr: SerializedGraphQLResponse -> sgr.executionResult }
                            .map { er: ExecutionResult ->
                                objectMapper.valueToTree<JsonNode>(er.toSpecification())
                            }
                            .doOnError { t: Throwable ->
                                logger.error(
                                    "materialize_features: [ status: failed ][ type: {}, message: {} ]",
                                    t.toOption()
                                        .filterIsInstance<ServiceError>()
                                        .and(ServiceError::class.simpleName.toOption())
                                        .orElse { t::class.simpleName.toOption() }
                                        .getOrElse { "<NA>" },
                                    t.message,
                                    t
                                )
                            }
                            .onErrorResume { t: Throwable ->
                                Mono.fromSupplier {
                                    JsonNodeFactory.instance
                                        .objectNode()
                                        .putNull("data")
                                        .set<ObjectNode>(
                                            "errors",
                                            JsonNodeFactory.instance
                                                .arrayNode(1)
                                                .add(
                                                    JsonNodeFactory.instance
                                                        .objectNode()
                                                        .put("errorType", t::class.simpleName)
                                                        .put("message", t.message)
                                                )
                                        )
                                }
                            }
                    }
                    .map { jn: JsonNode -> GenericMessage<JsonNode>(jn, m.headers) }
            }
        }
    }

    private fun convertJsonNodeIntoSchemaFormatJsonNode(jn: JsonNode): JsonNode {
        val mapToObjectNode: (Map<String, JsonNode>) -> JsonNode = { m: Map<String, JsonNode> ->
            m.foldLeft(JsonNodeFactory.instance.objectNode()) {
                on: ObjectNode,
                (k: String, v: JsonNode) ->
                on.set(k, v)
            }
        }
        val durationNodeToShowSpecificField: (JsonNode) -> Pair<String, JsonNode> = { j: JsonNode ->
            when {
                j.get("type").asText("") == "TV Show" -> {
                    "numberOfSeasons" to
                        JsonNodeFactory.instance.numberNode(
                            Regex("(?<numberOfSeasons>\\d+) [Ss]easons?")
                                .find(j.get("duration").asText(""))
                                ?.groups
                                ?.get("numberOfSeasons")
                                ?.value
                                ?.toIntOrNull() ?: 0
                        )
                }
                else -> {
                    "duration" to
                        JsonNodeFactory.instance.numberNode(
                            Regex("(?<duration>\\d+) min")
                                .find(j.get("duration").asText(""))
                                ?.groups
                                ?.get("duration")
                                ?.value
                                ?.toIntOrNull() ?: 0
                        )
                }
            }
        }
        return mapToObjectNode.invoke(
            mapOf(
                "show" to
                    mapToObjectNode.invoke(
                        mapOf(
                            "show_id" to jn.get("show_id"),
                            "type" to jn.get("type"),
                            "title" to jn.get("title"),
                            "director" to
                                mapToObjectNode.invoke(mapOf("name" to jn.get("director"))),
                            "cast" to
                                jn.get("cast")
                                    .toOption()
                                    .filterIsInstance<TextNode>()
                                    .map { tn: TextNode ->
                                        tn.textValue()?.splitToSequence(", ") ?: emptySequence()
                                    }
                                    .fold(::emptySequence, ::identity)
                                    .filter(String::isNotBlank)
                                    .map { n: String ->
                                        mapToObjectNode.invoke(
                                            mapOf("name" to JsonNodeFactory.instance.textNode(n))
                                        )
                                    }
                                    .fold(JsonNodeFactory.instance.arrayNode()) { an, n ->
                                        an.add(n)
                                    },
                            "country" to jn.get("country"),
                            "date_added" to jn.get("date_added"),
                            "release_year" to
                                JsonNodeFactory.instance.numberNode(
                                    jn.get("release_year").asText("").toIntOrNull() ?: 0
                                ),
                            "rating" to jn.get("rating"),
                            durationNodeToShowSpecificField(jn),
                            "listed_in" to
                                jn.get("listed_in")
                                    .toOption()
                                    .filterIsInstance<TextNode>()
                                    .map { tn: TextNode ->
                                        tn.textValue()?.splitToSequence(", ") ?: emptySequence()
                                    }
                                    .fold(::emptySequence, ::identity)
                                    .filter(String::isNotBlank)
                                    .fold(JsonNodeFactory.instance.arrayNode()) { an, g ->
                                        an.add(g)
                                    },
                            "description" to jn.get("description")
                        )
                    )
            )
        )
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
                            .dataLoaderRegistry(
                                DataLoaderRegistry.newRegistry()
                                    .register("show", createShowDataLoader())
                                    .build()
                            )
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

    private fun executeGraphQLIntrospectionRequestWithSchema(
        netflixShowsGraphQLSchema: GraphQLSchema
    ): Mono<JsonNode> {
        logger.info(
            "execute_graphql_introspection_request_with_schema: [ query[0:10]: {} ]",
            IntrospectionQuery.INTROSPECTION_QUERY.asSequence().take(10).joinToString("", "", "...")
        )
        return Mono.fromFuture(
                GraphQL.newGraphQL(netflixShowsGraphQLSchema)
                    .build()
                    .executeAsync(
                        ExecutionInput.newExecutionInput()
                            .query(IntrospectionQuery.INTROSPECTION_QUERY)
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

    private fun createShowDataLoader(): DataLoader<GQLOperationPath, JsonNode> {
        val mappedBatchLoaderWithContext: MappedBatchLoaderWithContext<GQLOperationPath, JsonNode> =
            MappedBatchLoaderWithContext {
                keys: MutableSet<GQLOperationPath>,
                environment: BatchLoaderEnvironment ->
                logger.info(
                    "mapped_batch_loader_with_context: [ status: loading ]\n[ keys: {}, \nenvironment.context: {}, \nenvironment.key_context[:]: {}\n ]",
                    keys.asSequence().joinToString(",\n "),
                    environment.getContext(),
                    environment.keyContexts.asSequence().joinToString(",\n ", "{ ", " }") { (k, v)
                        ->
                        "$k=${v::class.qualifiedName }}"
                    }
                )
                CompletableFuture.supplyAsync {
                    logger.info("mapped_batch_loader_with_context: [ status: dispatched ]")
                    keys
                        .asSequence()
                        .map { k: GQLOperationPath ->
                            k to JsonNodeFactory.instance.textNode(k.toString())
                        }
                        .toMap()
                }
            }
        return DataLoaderFactory.newMappedDataLoader(
            mappedBatchLoaderWithContext,
            DataLoaderOptions.newOptions()
                .setBatchLoaderContextProvider { null }
                .setStatisticsCollector { SimpleStatisticsCollector() }
        )
    }
}
