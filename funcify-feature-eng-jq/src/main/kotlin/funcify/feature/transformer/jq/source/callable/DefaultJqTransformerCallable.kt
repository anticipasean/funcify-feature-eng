package funcify.feature.transformer.jq.source.callable

import arrow.core.filterIsInstance
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import funcify.feature.error.ServiceError
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.json.GraphQLValueToJsonNodeConverter
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.transformer.TransformerCallable
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.transformer.jq.JqTransformer
import graphql.language.Value
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLTypeUtil
import kotlinx.collections.immutable.ImmutableMap
import org.slf4j.Logger
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

internal class DefaultJqTransformerCallable(
    private val sourceName: String,
    private val jqTransformer: JqTransformer,
    override val transformerFieldCoordinates: FieldCoordinates,
    override val transformerPath: GQLOperationPath,
    override val transformerGraphQLFieldDefinition: GraphQLFieldDefinition,
) : TransformerCallable {

    companion object {
        private val METHOD_TAG: String =
            StandardNamingConventions.SNAKE_CASE.deriveName(
                    DefaultJqTransformerCallable::class.simpleName ?: "<NA>"
                )
                .qualifiedForm + ".invoke"
        private val logger: Logger = loggerFor<DefaultJqTransformerCallable>()
    }

    override val argumentsByName: ImmutableMap<String, GraphQLArgument> by lazy {
        transformerGraphQLFieldDefinition.arguments
            .asSequence()
            .map { a: GraphQLArgument -> a.name to a }
            .reducePairsToPersistentMap()
    }

    override val argumentsByPath: ImmutableMap<GQLOperationPath, GraphQLArgument> by lazy {
        transformerGraphQLFieldDefinition.arguments
            .asSequence()
            .map { a: GraphQLArgument -> transformerPath.transform { argument(a.name) } to a }
            .reducePairsToPersistentMap()
    }

    private val defaultValuesByArgumentName: ImmutableMap<String, JsonNode> by lazy {
        argumentsByName
            .asSequence()
            .filter { (_: String, a: GraphQLArgument) -> a.hasSetDefaultValue() }
            .map { (n: String, a: GraphQLArgument) ->
                a.argumentDefaultValue
                    .toOption()
                    .filterIsInstance<Value<*>>()
                    .flatMap(GraphQLValueToJsonNodeConverter)
                    .map { jn: JsonNode -> n to jn }
            }
            .flatMapOptions()
            .reducePairsToPersistentMap()
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
                            in defaultValuesByArgumentName -> {
                                Mono.just(n to defaultValuesByArgumentName[n]!!)
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
