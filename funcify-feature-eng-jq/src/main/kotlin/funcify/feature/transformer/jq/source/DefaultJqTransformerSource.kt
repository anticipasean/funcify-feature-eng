package funcify.feature.transformer.jq.source

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import arrow.core.filterIsInstance
import arrow.core.identity
import arrow.core.lastOrNone
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import funcify.feature.error.ServiceError
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.json.GraphQLValueToJsonNodeConverter
import funcify.feature.schema.path.operation.AliasedFieldSegment
import funcify.feature.schema.path.operation.FieldSegment
import funcify.feature.schema.path.operation.FragmentSpreadSegment
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.path.operation.InlineFragmentSegment
import funcify.feature.schema.path.operation.SelectionSegment
import funcify.feature.schema.transformer.TransformerCallable
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.transformer.jq.JqTransformer
import funcify.feature.transformer.jq.JqTransformerSource
import graphql.language.SDLDefinition
import graphql.language.Value
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLTypeUtil
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import org.slf4j.Logger
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

internal class DefaultJqTransformerSource(
    override val name: String,
    override val sourceSDLDefinitions: PersistentSet<SDLDefinition<*>>,
    override val jqTransformersByName: PersistentMap<String, JqTransformer> = persistentMapOf(),
) : JqTransformerSource {

    companion object {
        private class DefaultJqTransformerCallableBuilder(
            private val sourceName: String,
            private val jqTransformersByName: ImmutableMap<String, JqTransformer>,
            private var transformerFieldCoordinates: FieldCoordinates? = null,
            private var transformerPath: GQLOperationPath? = null,
            private var transformerGraphQLFieldDefinition: GraphQLFieldDefinition? = null
        ) : TransformerCallable.Builder {

            companion object {
                private val logger: Logger = loggerFor<DefaultJqTransformerCallableBuilder>()
            }

            override fun selectTransformer(
                coordinates: FieldCoordinates,
                path: GQLOperationPath,
                graphQLFieldDefinition: GraphQLFieldDefinition,
            ): TransformerCallable.Builder =
                this.apply {
                    this.transformerFieldCoordinates = coordinates
                    this.transformerPath = path
                    this.transformerGraphQLFieldDefinition = graphQLFieldDefinition
                }

            override fun build(): TransformerCallable {
                if (logger.isDebugEnabled) {
                    logger.debug(
                        "build: [ source.name: {}, transformer.field_coordinates: {} ]",
                        sourceName,
                        transformerFieldCoordinates
                    )
                }
                return eagerEffect<String, TransformerCallable> {
                        ensureNotNull(transformerFieldCoordinates) {
                            "transformer_field_coordinates not provided"
                        }
                        ensureNotNull(transformerPath) { "transformer_path not provided" }
                        ensureNotNull(transformerGraphQLFieldDefinition) {
                            "transformer_graphql_field_definition not provided"
                        }
                        ensure(
                            transformerFieldCoordinates!!.fieldName ==
                                transformerGraphQLFieldDefinition!!.name
                        ) {
                            """transformer_field_coordinates.field_name does not match 
                                |transformer_graphql_field_definition.name"""
                                .flatten()
                        }
                        ensure(
                            transformerPath!!
                                .selection
                                .lastOrNone()
                                .mapNotNull { ss: SelectionSegment ->
                                    when (ss) {
                                        is FieldSegment -> ss.fieldName
                                        is AliasedFieldSegment -> ss.fieldName
                                        is FragmentSpreadSegment -> ss.selectedField.fieldName
                                        is InlineFragmentSegment -> ss.selectedField.fieldName
                                    }
                                }
                                .filter { fn: String ->
                                    fn == transformerFieldCoordinates!!.fieldName
                                }
                                .isDefined()
                        ) {
                            """transformer_path[-1].field_name does not match 
                            |transformer_field_coordinates.field_name"""
                                .flatten()
                        }
                        ensure(transformerFieldCoordinates!!.fieldName in jqTransformersByName) {
                            """transformer_field_coordinates.field_name [ field_name: %s ] does not match 
                            |transformer_name within jq_transformers_by_name mappings"""
                                .flatten()
                                .format(transformerFieldCoordinates?.fieldName)
                        }

                        JqTransformerCallable(
                            sourceName = sourceName,
                            jqTransformer =
                                jqTransformersByName[transformerFieldCoordinates!!.fieldName]!!,
                            transformerFieldCoordinates = transformerFieldCoordinates!!,
                            transformerPath = transformerPath!!,
                            transformerGraphQLFieldDefinition = transformerGraphQLFieldDefinition!!
                        )
                    }
                    .fold(
                        { message: String ->
                            throw ServiceError.of(
                                "unable to create %s [ message: %s ]",
                                JqTransformerCallable::class.simpleName,
                                message
                            )
                        },
                        ::identity
                    )
            }
        }

        private class JqTransformerCallable(
            private val sourceName: String,
            private val jqTransformer: JqTransformer,
            override val transformerFieldCoordinates: FieldCoordinates,
            override val transformerPath: GQLOperationPath,
            override val transformerGraphQLFieldDefinition: GraphQLFieldDefinition,
        ) : TransformerCallable {

            override val argumentsByName: ImmutableMap<String, GraphQLArgument> by lazy {
                transformerGraphQLFieldDefinition.arguments
                    .asSequence()
                    .map { a: GraphQLArgument -> a.name to a }
                    .reducePairsToPersistentMap()
            }

            override val argumentsByPath: ImmutableMap<GQLOperationPath, GraphQLArgument> by lazy {
                transformerGraphQLFieldDefinition.arguments
                    .asSequence()
                    .map { a: GraphQLArgument ->
                        transformerPath.transform { argument(a.name) } to a
                    }
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

            companion object {
                private val METHOD_TAG: String =
                    StandardNamingConventions.SNAKE_CASE.deriveName(
                            JqTransformerCallable::class.simpleName ?: "<NA>"
                        )
                        .qualifiedForm + ".invoke"
                private val logger: Logger = loggerFor<JqTransformerCallable>()
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
    }

    override fun builder(): TransformerCallable.Builder {
        return DefaultJqTransformerCallableBuilder(name, jqTransformersByName)
    }
}
