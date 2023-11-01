package funcify.feature.transformer.jq.jackson

import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.orElse
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ContainerNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.jsonSchema.JsonSchema
import com.github.fge.jsonschema.main.JsonSchemaFactory
import funcify.feature.error.ServiceError
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.transformer.jq.JqTransformer
import graphql.language.Type
import net.thisptr.jackson.jq.JsonQuery
import net.thisptr.jackson.jq.Scope
import org.slf4j.Logger
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import reactor.core.publisher.Mono

internal class DefaultJacksonJqTransformer(
    private val rootScope: Scope,
    private val jsonQuery: JsonQuery,
    override val name: String,
    override val expression: String,
    override val inputSchema: JsonSchema,
    override val outputSchema: JsonSchema,
    override val graphQLSDLInputType: Type<*>,
    override val graphQLSDLOutputType: Type<*>,
) : JqTransformer {

    companion object {
        private val logger: Logger = loggerFor<DefaultJacksonJqTransformer>()
    }

    private val objectMapper: ObjectMapper by lazy { ObjectMapper() }
    private val inputSchemaAsNode: JsonNode by lazy { objectMapper.valueToTree(inputSchema) }
    private val inputValidationSchema: com.github.fge.jsonschema.main.JsonSchema by lazy {
        JsonSchemaFactory.byDefault().getJsonSchema(inputSchemaAsNode)
    }

    override fun transform(input: JsonNode): Mono<out JsonNode> {
        if (logger.isDebugEnabled) {
            logger.debug("transform: [ name: {}, input: {} ]", name, input)
        }
        return Mono.fromSupplier {
                convertIntoScalarNodeIfInputSchemaImpliesUnaryScalarOperation(input)
            }
            .map { j: JsonNode -> validateInputNodeBeforeExpressionEvaluation(j) }
            .flatMapMany { j: JsonNode -> createResultPublisherFromJsonQueryEvaluation(j) }
            .doOnNext { jn: JsonNode? ->
                if (logger.isDebugEnabled) {
                    logger.debug("transform: [ on_next: { json_node: {} } ]", jn)
                }
            }
            .map { jn: JsonNode? -> jn ?: JsonNodeFactory.instance.nullNode() }
            .collectList()
            .flatMap { jns: List<JsonNode> ->
                convertResultListIntoOutputSchemaFormatIfPossible(jns)
            }
    }

    private fun convertIntoScalarNodeIfInputSchemaImpliesUnaryScalarOperation(
        inputNode: JsonNode
    ): JsonNode {
        return when (inputNode) {
            !is ContainerNode<*> -> {
                inputNode
            }
            else -> {
                when {
                    // TODO: Consider whether input schema type
                    // [com.fasterxml.jackson.module.jsonSchema.types.AnySchema] warrant keeping
                    // input_node as-is
                    inputSchema.isArraySchema || inputSchema.isObjectSchema -> {
                        inputNode
                    }
                    else -> {
                        inputNode
                            .toOption()
                            .filterIsInstance<ArrayNode>()
                            .filter { an: ArrayNode -> an.size() == 1 }
                            .map { an: ArrayNode -> an.get(0) }
                            .orElse {
                                inputNode
                                    .toOption()
                                    .filterIsInstance<ObjectNode>()
                                    .filter { on: ObjectNode -> on.size() == 1 }
                                    .mapNotNull { on: ObjectNode -> on.elements().next() }
                            }
                            .getOrElse { inputNode }
                    }
                }
            }
        }
    }

    private fun validateInputNodeBeforeExpressionEvaluation(j: JsonNode): JsonNode {
        return try {
            if (inputValidationSchema.validInstance(j)) {
                j
            } else {
                throw ServiceError.of(
                    "input_node [ %s ] flagged as invalid per input_schema [ %s ]",
                    j,
                    inputSchemaAsNode
                )
            }
        } catch (t: Throwable) {
            when (t) {
                is ServiceError -> {
                    throw t
                }
                else -> {
                    throw ServiceError.builder()
                        .message("unable to validate schema of input_node")
                        .cause(t)
                        .build()
                }
            }
        }
    }

    /**
     * Should have nullable [JsonNode] content type because underlying Java API may return that, so
     * it should be handled
     */
    private fun createResultPublisherFromJsonQueryEvaluation(j: JsonNode): Flux<JsonNode?> {
        return Flux.create<JsonNode?> { s: FluxSink<JsonNode?> ->
            try {
                jsonQuery.apply(Scope.newChildScope(rootScope), j, s::next)
                s.complete()
            } catch (t: Throwable) {
                s.error(
                    ServiceError.builder().message("json query execution error").cause(t).build()
                )
            }
        }
    }

    private fun convertResultListIntoOutputSchemaFormatIfPossible(
        resultNodesList: List<JsonNode>
    ): Mono<out JsonNode> {
        return when {
            outputSchema.isArraySchema -> {
                when {
                    resultNodesList.isEmpty() -> {
                        Mono.just(JsonNodeFactory.instance.arrayNode(0))
                    }
                    resultNodesList.size == 1 && resultNodesList[0].isArray -> {
                        Mono.just(resultNodesList[0])
                    }
                    else -> {
                        Mono.fromSupplier {
                            resultNodesList
                                .asSequence()
                                .fold(
                                    JsonNodeFactory.instance.arrayNode(resultNodesList.size),
                                    ArrayNode::add
                                )
                        }
                    }
                }
            }
            outputSchema.isObjectSchema -> {
                when {
                    resultNodesList.isEmpty() -> {
                        Mono.just(JsonNodeFactory.instance.objectNode())
                    }
                    resultNodesList.size == 1 -> {
                        Mono.just(resultNodesList[0])
                    }
                    else -> {
                        Mono.error<JsonNode> {
                            ServiceError.of(
                                """more than one json_node result received 
                                |from jq as output for object type
                                |from json_query: [ %s ]"""
                                    .flatten(),
                                resultNodesList.asSequence().withIndex().joinToString(", ") {
                                    (idx: Int, jn: JsonNode) ->
                                    "[$idx]: \"$jn\""
                                }
                            )
                        }
                    }
                }
            }
            else -> {
                when {
                    resultNodesList.isEmpty() -> {
                        Mono.just(JsonNodeFactory.instance.nullNode())
                    }
                    resultNodesList.size == 1 -> {
                        Mono.just(resultNodesList[0])
                    }
                    else -> {
                        Mono.error<JsonNode> {
                            ServiceError.of(
                                """more than one json_node result received 
                                |from jq as output for scalar type
                                |from json_query: [ %s ]"""
                                    .flatten(),
                                resultNodesList.asSequence().withIndex().joinToString(", ") {
                                    (idx: Int, jn: JsonNode) ->
                                    "[$idx]: \"$jn\""
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
