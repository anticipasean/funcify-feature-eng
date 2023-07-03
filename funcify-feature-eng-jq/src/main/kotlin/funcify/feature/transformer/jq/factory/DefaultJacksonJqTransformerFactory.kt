package funcify.feature.transformer.jq.factory

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.jsonSchema.JsonSchema
import funcify.feature.error.ServiceError
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.transformer.jq.JacksonJqTransformer
import graphql.language.Type
import net.thisptr.jackson.jq.JsonQuery
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
            private var outputSchema: JsonSchema? = null
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
                        ensureNotNull(inputSchema) { "inputSchema has not been provided" }
                        ensureNotNull(outputSchema) { "outputSchema has not been provided" }
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
