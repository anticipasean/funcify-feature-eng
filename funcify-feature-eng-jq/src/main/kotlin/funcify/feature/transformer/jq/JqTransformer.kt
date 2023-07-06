package funcify.feature.transformer.jq

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.jsonSchema.JsonSchema
import funcify.feature.tools.container.attempt.Try
import graphql.language.Type
import net.thisptr.jackson.jq.JsonQuery
import reactor.core.publisher.Mono

/**
 * @author smccarron
 * @created 2023-07-03
 */
interface JqTransformer {

    val name: String

    val expression: String

    val jsonQuery: JsonQuery

    val inputSchema: JsonSchema

    val outputSchema: JsonSchema

    val graphQLSDLInputType: Type<*>

    val graphQLSDLOutputType: Type<*>

    fun transform(input: JsonNode): Mono<out JsonNode>

    interface Builder {

        fun name(name: String): Builder

        fun expression(expression: String): Builder

        fun inputSchema(inputSchema: JsonSchema): Builder

        fun outputSchema(outputSchema: JsonSchema): Builder

        fun build(): Try<JqTransformer>
    }
}
