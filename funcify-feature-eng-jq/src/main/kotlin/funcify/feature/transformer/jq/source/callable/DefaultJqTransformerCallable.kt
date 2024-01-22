package funcify.feature.transformer.jq.source.callable

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import funcify.feature.error.ServiceError
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.transformer.TransformerCallable
import funcify.feature.schema.transformer.TransformerSpecifiedTransformerSource
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.transformer.jq.JqTransformer
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLTypeUtil
import kotlinx.collections.immutable.ImmutableMap
import org.slf4j.Logger
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

internal class DefaultJqTransformerCallable(
    override val transformerSpecifiedTransformerSource: TransformerSpecifiedTransformerSource,
    private val jqTransformer: JqTransformer,
) : TransformerCallable {

    companion object {
        private val METHOD_TAG: String =
            StandardNamingConventions.SNAKE_CASE.deriveName(
                    DefaultJqTransformerCallable::class.simpleName ?: "<NA>"
                )
                .qualifiedForm + ".invoke"
        private val logger: Logger = loggerFor<DefaultJqTransformerCallable>()
    }

    override fun invoke(arguments: ImmutableMap<String, JsonNode>): Mono<JsonNode> {
        logger.info(
            "{}: [ arguments.keys: {} ]",
            METHOD_TAG,
            arguments.keys.asSequence().joinToString(", ", "{ ", " }")
        )
        return Flux.merge(
                argumentsByName
                    .asSequence()
                    .map { (n: String, a: GraphQLArgument) ->
                        when (n) {
                            in arguments -> {
                                Mono.just(n to arguments[n]!!)
                            }
                            in defaultArgumentValuesByName -> {
                                Mono.just(n to defaultArgumentValuesByName[n]!!)
                            }
                            else -> {
                                Mono.error {
                                    ServiceError.of(
                                        """no argument value has been provided for [ argument: { name: %s, type: %s } ] 
                                        |nor does a default value exist for it on transformer [ coordinates: %s ]"""
                                            .flatten(),
                                        a.name,
                                        GraphQLTypeUtil.simplePrint(a.type),
                                        transformerFieldCoordinates
                                    )
                                }
                            }
                        }
                    }
                    .asIterable()
            )
            .reduceWith(JsonNodeFactory.instance::objectNode) {
                on: ObjectNode,
                (n: String, v: JsonNode) ->
                on.set(n, v)
            }
            .flatMap { on: ObjectNode -> jqTransformer.transform(on) }
    }
}
