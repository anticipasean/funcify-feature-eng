package funcify.feature.transformer.jq.factory

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.jsonSchema.JsonSchema
import funcify.feature.error.ServiceError
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.transformer.jq.JacksonJqTransformer
import graphql.language.Type
import net.thisptr.jackson.jq.JsonQuery
import net.thisptr.jackson.jq.Versions
import net.thisptr.jackson.jq.exception.JsonQueryException
import org.slf4j.Logger
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-07-03
 */
internal class DefaultJacksonJqTransformerFactory : JacksonJqTransformerFactory {

    companion object {

        internal class DefaultBuilder(
            private var name: String? = null,
            private var expression: String? = null,
            private var inputSchema: JsonSchema? = null,
            private var outputSchema: JsonSchema? = null,
        ) : JacksonJqTransformer.Builder {

            companion object {
                private val logger: Logger = loggerFor<DefaultJacksonJqTransformerFactory>()
            }

            override fun name(name: String): JacksonJqTransformer.Builder {
                this.name = name
                return this
            }

            override fun expression(expression: String): JacksonJqTransformer.Builder {
                this.expression = expression
                return this
            }

            override fun inputSchema(inputSchema: JsonSchema): JacksonJqTransformer.Builder {
                this.inputSchema = inputSchema
                return this
            }

            override fun outputSchema(outputSchema: JsonSchema): JacksonJqTransformer.Builder {
                this.outputSchema = outputSchema
                return this
            }

            override fun build(): Result<JacksonJqTransformer> {
                if (logger.isDebugEnabled) {
                    logger.debug("build: [ name: {} ]", name)
                }
                return eagerEffect<String, JacksonJqTransformer> {
                        ensureNotNull(name) { "name has not been provided" }
                        ensureNotNull(expression) { "expression has not been provided" }
                        ensure(expression!!.isNotBlank()) { "the provided expression is blank" }
                        ensureNotNull(inputSchema) { "inputSchema has not been provided" }
                        ensureNotNull(outputSchema) { "outputSchema has not been provided" }
                        val jq: JsonQuery =
                            try {
                                JsonQuery.compile(expression, Versions.JQ_1_6)
                            } catch (e: JsonQueryException) {
                                shift(
                                    """expression [ %s ] did not compile successfully: 
                                        |[ type: %s, message: %s ]"""
                                        .flatten()
                                        .format(expression, e::class.qualifiedName, e.message)
                                )
                            }
                        TODO()
                    }
                    .fold(
                        { message: String ->
                            logger.error("build: [ status: failed ][ message: {} ]", message)
                            Result.failure(ServiceError.of(message))
                        },
                        { j: JacksonJqTransformer -> Result.success(j) }
                    )
            }
        }

        internal class DefaultJacksonJqTransformer(
            override val name: String,
            override val expression: String,
            override val jsonQuery: JsonQuery,
            override val inputSchema: JsonSchema,
            override val outputSchema: JsonSchema,
            override val graphQLSDLInputType: Type<*>,
            override val graphQLSDLOutputType: Type<*>,
        ) : JacksonJqTransformer {

            override fun transform(input: JsonNode): Mono<JsonNode> {
                TODO("Not yet implemented")
            }
        }
    }

    override fun builder(): JacksonJqTransformer.Builder {
        TODO("Not yet implemented")
    }
}
