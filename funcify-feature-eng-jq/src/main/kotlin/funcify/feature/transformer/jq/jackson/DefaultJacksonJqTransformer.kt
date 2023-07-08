package funcify.feature.transformer.jq.jackson

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
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

    override fun transform(input: JsonNode): Mono<out JsonNode> {
        if (logger.isDebugEnabled) {
            logger.debug("transform: [ name: {}, input: {} ]", name, input)
        }
        return Mono.fromCallable {
                try {
                    if (
                        JsonSchemaFactory.byDefault()
                            .getJsonSchema(inputSchemaAsNode)
                            .validInstance(input)
                    ) {
                        input
                    } else {
                        throw ServiceError.of(
                            "input_node [ %s ] flagged as invalid per input_schema [ %s ]",
                            input,
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
            .flatMapMany { j: JsonNode ->
                Flux.create<JsonNode?> { s: FluxSink<JsonNode?> ->
                    try {
                        jsonQuery.apply(Scope.newChildScope(rootScope), j, s::next)
                        s.complete()
                    } catch (t: Throwable) {
                        s.error(
                            ServiceError.builder()
                                .message("json query execution error")
                                .cause(t)
                                .build()
                        )
                    }
                }
            }
            .doOnNext { jn: JsonNode? ->
                if (logger.isDebugEnabled) {
                    logger.debug("transform: [ on_next: { json_node: {} } ]", jn)
                }
            }
            .map { jn: JsonNode? -> jn ?: JsonNodeFactory.instance.nullNode() }
            .collectList()
            .flatMap { jns: List<JsonNode> ->
                when {
                    outputSchema.isArraySchema -> {
                        when {
                            jns.isEmpty() -> {
                                Mono.just(JsonNodeFactory.instance.arrayNode(0))
                            }
                            jns.size == 1 && jns[0].isArray -> {
                                Mono.just(jns[0])
                            }
                            else -> {
                                Mono.fromSupplier {
                                    jns.asSequence()
                                        .fold(
                                            JsonNodeFactory.instance.arrayNode(jns.size),
                                            ArrayNode::add
                                        )
                                }
                            }
                        }
                    }
                    outputSchema.isObjectSchema -> {
                        when {
                            jns.isEmpty() -> {
                                Mono.just(JsonNodeFactory.instance.objectNode())
                            }
                            jns.size == 1 -> {
                                Mono.just(jns[0])
                            }
                            else -> {
                                Mono.error<JsonNode> {
                                    ServiceError.of(
                                        """more than one json_node result received 
                                        |from jq as output for object type
                                        |from json_query: [ %s ]"""
                                            .flatten(),
                                        jns.asSequence().withIndex().joinToString(", ") {
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
                            jns.isEmpty() -> {
                                Mono.just(JsonNodeFactory.instance.nullNode())
                            }
                            jns.size == 1 -> {
                                Mono.just(jns[0])
                            }
                            else -> {
                                Mono.error<JsonNode> {
                                    ServiceError.of(
                                        """more than one json_node result received 
                                        |from jq as output for scalar type
                                        |from json_query: [ %s ]"""
                                            .flatten(),
                                        jns.asSequence().withIndex().joinToString(", ") {
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
}
