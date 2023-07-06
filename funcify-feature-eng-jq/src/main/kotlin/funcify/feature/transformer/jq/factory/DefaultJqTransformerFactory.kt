package funcify.feature.transformer.jq.factory

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.module.jsonSchema.JsonSchema
import com.github.fge.jsonschema.main.JsonSchemaFactory
import funcify.feature.error.ServiceError
import funcify.feature.schema.sdl.JsonSchemaToNullableSDLTypeComposer
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.transformer.jq.JqTransformer
import graphql.language.Type
import net.thisptr.jackson.jq.BuiltinFunctionLoader
import net.thisptr.jackson.jq.JsonQuery
import net.thisptr.jackson.jq.Scope
import net.thisptr.jackson.jq.Versions
import net.thisptr.jackson.jq.exception.JsonQueryException
import net.thisptr.jackson.jq.module.loaders.BuiltinModuleLoader
import org.slf4j.Logger
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-07-03
 */
internal class DefaultJqTransformerFactory : JqTransformerFactory {

    companion object {

        private val rootScope: Try<Scope> by lazy {
            try {
                Try.success(
                    Scope.newEmptyScope().apply {
                        BuiltinFunctionLoader.getInstance().loadFunctions(Versions.JQ_1_6, this)
                        this.moduleLoader = BuiltinModuleLoader.getInstance()
                    }
                )
            } catch (t: Throwable) {
                Try.failure(
                    ServiceError.builder()
                        .message("failed to create root_scope for jq transformers")
                        .cause(t)
                        .build()
                )
            }
        }

        internal class DefaultBuilder(
            private val rootScope: Scope,
            private var name: String? = null,
            private var expression: String? = null,
            private var inputSchema: JsonSchema? = null,
            private var outputSchema: JsonSchema? = null,
        ) : JqTransformer.Builder {

            companion object {
                private val logger: Logger = loggerFor<DefaultJqTransformerFactory>()
            }

            override fun name(name: String): JqTransformer.Builder {
                this.name = name
                return this
            }

            override fun expression(expression: String): JqTransformer.Builder {
                this.expression = expression
                return this
            }

            override fun inputSchema(inputSchema: JsonSchema): JqTransformer.Builder {
                this.inputSchema = inputSchema
                return this
            }

            override fun outputSchema(outputSchema: JsonSchema): JqTransformer.Builder {
                this.outputSchema = outputSchema
                return this
            }

            override fun build(): Try<JqTransformer> {
                if (logger.isDebugEnabled) {
                    logger.debug("build: [ name: {} ]", name)
                }
                return eagerEffect<ServiceError, JqTransformer> {
                        ensureNotNull(name) { ServiceError.of("name has not been provided") }
                        ensureNotNull(expression) {
                            ServiceError.of("expression has not been provided")
                        }
                        ensure(expression!!.isNotBlank()) {
                            ServiceError.of("the provided expression is blank")
                        }
                        ensureNotNull(inputSchema) {
                            ServiceError.of("inputSchema has not been provided")
                        }
                        ensureNotNull(outputSchema) {
                            ServiceError.of("outputSchema has not been provided")
                        }
                        val jq: JsonQuery =
                            try {
                                JsonQuery.compile(expression, Versions.JQ_1_6)
                            } catch (e: JsonQueryException) {
                                shift(
                                    ServiceError.builder()
                                        .message(
                                            "expression [ %s ] did not compile successfully"
                                                .flatten()
                                                .format(expression)
                                        )
                                        .cause(e)
                                        .build()
                                )
                            }
                        val sdlInputType: Type<*> =
                            try {
                                JsonSchemaToNullableSDLTypeComposer.invoke(inputSchema!!)
                            } catch (e: Exception) {
                                shift(
                                    when (e) {
                                        is ServiceError -> {
                                            e
                                        }
                                        else -> {
                                            ServiceError.builder()
                                                .message("input sdl_type determination error")
                                                .cause(e)
                                                .build()
                                        }
                                    }
                                )
                            }
                        val sdlOutputType: Type<*> =
                            try {
                                JsonSchemaToNullableSDLTypeComposer.invoke(outputSchema!!)
                            } catch (e: Exception) {
                                shift(
                                    when (e) {
                                        is ServiceError -> {
                                            e
                                        }
                                        else -> {
                                            ServiceError.builder()
                                                .message("output sdl_type determination error")
                                                .cause(e)
                                                .build()
                                        }
                                    }
                                )
                            }
                        DefaultJqTransformer(
                            rootScope = rootScope,
                            name = name!!,
                            expression = expression!!,
                            jsonQuery = jq,
                            inputSchema = inputSchema!!,
                            outputSchema = outputSchema!!,
                            graphQLSDLInputType = sdlInputType,
                            graphQLSDLOutputType = sdlOutputType
                        )
                    }
                    .fold(
                        { se: ServiceError ->
                            logger.error(
                                "build: [ status: failed ][ service_error: {} ]",
                                se.toJsonNode()
                            )
                            Try.failure(se)
                        },
                        { j: JqTransformer -> Try.success(j) }
                    )
            }
        }

        internal class DefaultJqTransformer(
            private val rootScope: Scope,
            override val name: String,
            override val expression: String,
            override val jsonQuery: JsonQuery,
            override val inputSchema: JsonSchema,
            override val outputSchema: JsonSchema,
            override val graphQLSDLInputType: Type<*>,
            override val graphQLSDLOutputType: Type<*>,
        ) : JqTransformer {

            companion object {
                private val logger: Logger = loggerFor<DefaultJqTransformer>()
            }
            private val objectMapper: ObjectMapper by lazy { ObjectMapper() }
            private val inputSchemaAsNode: JsonNode by lazy {
                objectMapper.valueToTree(inputSchema)
            }

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
                            outputSchema.isObjectSchema || outputSchema.isAnySchema -> {
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
    }

    override fun builder(): JqTransformer.Builder {
        return DefaultBuilder(rootScope = rootScope.orElseThrow())
    }
}
