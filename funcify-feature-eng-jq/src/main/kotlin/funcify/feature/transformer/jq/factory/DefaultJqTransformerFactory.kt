package funcify.feature.transformer.jq.factory

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import com.fasterxml.jackson.module.jsonSchema.JsonSchema
import funcify.feature.error.ServiceError
import funcify.feature.schema.sdl.JsonSchemaToNullableSDLTypeComposer
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.transformer.jq.JqTransformer
import funcify.feature.transformer.jq.JqTransformerFactory
import funcify.feature.transformer.jq.jackson.DefaultJacksonJqTransformer
import graphql.language.Type
import net.thisptr.jackson.jq.BuiltinFunctionLoader
import net.thisptr.jackson.jq.JsonQuery
import net.thisptr.jackson.jq.Scope
import net.thisptr.jackson.jq.Versions
import net.thisptr.jackson.jq.exception.JsonQueryException
import net.thisptr.jackson.jq.module.loaders.BuiltinModuleLoader
import org.slf4j.Logger

/**
 * @author smccarron
 * @created 2023-07-03
 */
internal class DefaultJqTransformerFactory : JqTransformerFactory {

    companion object {

        private val rootJacksonJqScopeAttempt: Try<Scope> by lazy {
            Try.attempt {
                    Scope.newEmptyScope().apply {
                        BuiltinFunctionLoader.getInstance().loadFunctions(Versions.JQ_1_6, this)
                        this.moduleLoader = BuiltinModuleLoader.getInstance()
                    }
                }
                .mapFailure { t: Throwable ->
                    ServiceError.builder()
                        .message(
                            """failed to create jackson-jq root_scope 
                            |for jackson-jq transformers"""
                                .flatten()
                        )
                        .cause(t)
                        .build()
                }
        }

        internal class DefaultBuilder(
            private val rootJacksonJqScopeAttempt: Try<Scope>,
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
                        val rootScope: Scope =
                            try {
                                rootJacksonJqScopeAttempt.orElseThrow()
                            } catch (se: ServiceError) {
                                shift(se)
                            }
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
                        DefaultJacksonJqTransformer(
                            rootScope = rootScope,
                            jsonQuery = jq,
                            name = name!!,
                            expression = expression!!,
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
    }

    override fun builder(): JqTransformer.Builder {
        return DefaultBuilder(rootJacksonJqScopeAttempt = rootJacksonJqScopeAttempt)
    }
}
