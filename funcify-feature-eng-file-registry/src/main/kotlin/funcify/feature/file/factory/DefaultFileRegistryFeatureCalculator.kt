package funcify.feature.file.factory

import arrow.core.continuations.eagerEffect
import arrow.core.continuations.ensureNotNull
import arrow.core.filterIsInstance
import arrow.core.identity
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.error.ServiceError
import funcify.feature.file.FileRegistryFeatureCalculator
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.feature.FeatureCalculatorCallable
import funcify.feature.schema.json.GraphQLValueToJsonNodeConverter
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.tracking.TrackableValue
import funcify.feature.schema.transformer.TransformerCallable
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.language.SDLDefinition
import graphql.language.Value
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.InputValueWithState
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.plus
import org.slf4j.Logger
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

internal class DefaultFileRegistryFeatureCalculator(
    override val name: String,
    override val sourceSDLDefinitions: PersistentSet<SDLDefinition<*>>
) : FileRegistryFeatureCalculator {

    companion object {

        private class DefaultFeatureCalculatorCallableBuilder(
            private var featureFieldCoordinates: FieldCoordinates? = null,
            private var featurePath: GQLOperationPath? = null,
            private var featureGraphQLFieldDefinition: GraphQLFieldDefinition? = null,
            private var transformerCallable: TransformerCallable? = null
        ) : FeatureCalculatorCallable.Builder {

            override fun selectFeature(
                coordinates: FieldCoordinates,
                path: GQLOperationPath,
                graphQLFieldDefinition: GraphQLFieldDefinition,
            ): FeatureCalculatorCallable.Builder =
                this.apply {
                    featureFieldCoordinates = coordinates
                    featurePath = path
                    featureGraphQLFieldDefinition = graphQLFieldDefinition
                }

            override fun setTransformerCallable(
                transformerCallable: TransformerCallable
            ): FeatureCalculatorCallable.Builder =
                this.apply { this.transformerCallable = transformerCallable }

            override fun build(): FeatureCalculatorCallable {
                return eagerEffect<String, FeatureCalculatorCallable> {
                        ensureNotNull(featureFieldCoordinates) {
                            "feature_field_coordinates not provided"
                        }
                        ensureNotNull(featurePath) { "feature_path not provided" }
                        ensureNotNull(featureGraphQLFieldDefinition) {
                            "feature_graphql_field_definition not provided"
                        }
                        ensureNotNull(transformerCallable) { "transformer_callable not provided" }
                        DefaultFeatureCalculatorCallable(
                            featureCoordinates = featureFieldCoordinates!!,
                            featurePath = featurePath!!,
                            featureGraphQLFieldDefinition = featureGraphQLFieldDefinition!!,
                            transformerCallable = transformerCallable!!
                        )
                    }
                    .fold(
                        { message: String ->
                            throw ServiceError.of(
                                "unable to create %s [ message: %s ]",
                                DefaultFeatureCalculatorCallable::class.simpleName,
                                message
                            )
                        },
                        ::identity
                    )
            }
        }

        private class DefaultFeatureCalculatorCallable(
            override val featureCoordinates: FieldCoordinates,
            override val featurePath: GQLOperationPath,
            override val featureGraphQLFieldDefinition: GraphQLFieldDefinition,
            override val transformerCallable: TransformerCallable
        ) : FeatureCalculatorCallable {

            override val argumentsByName: ImmutableMap<String, GraphQLArgument> by lazy {
                featureGraphQLFieldDefinition.arguments
                    .asSequence()
                    .map { a: GraphQLArgument -> a.name to a }
                    .reducePairsToPersistentMap()
            }

            override val argumentsByPath: ImmutableMap<GQLOperationPath, GraphQLArgument> by lazy {
                featureGraphQLFieldDefinition.arguments
                    .asSequence()
                    .map { a: GraphQLArgument -> featurePath.transform { argument(a.name) } to a }
                    .reducePairsToPersistentMap()
            }

            companion object {
                private val METHOD_TAG: String =
                    StandardNamingConventions.SNAKE_CASE.deriveName(
                            DefaultFeatureCalculatorCallable::class.simpleName ?: "<NA>"
                        )
                        .qualifiedForm + ".invoke"
                private const val DEFAULT_UNARY_TRANSFORMER_ARGUMENT_NAME: String = "input"
                private val logger: Logger = loggerFor<DefaultFeatureCalculatorCallable>()
            }

            override fun invoke(
                trackableFeatureValue: TrackableValue<JsonNode>,
                arguments: ImmutableMap<GQLOperationPath, Mono<JsonNode>>,
            ): Mono<TrackableValue<JsonNode>> {
                logger.info(
                    "{}: [ feature_path: {}, feature_coordinates: {}, trackable_feature_value: {}, arguments.keys: {} ]",
                    METHOD_TAG,
                    featurePath,
                    featureCoordinates,
                    trackableFeatureValue,
                    arguments.keys.asSequence().joinToString(", ", "{ ", " }")
                )
                return Mono.defer {
                    when (trackableFeatureValue) {
                        is TrackableValue.PlannedValue<JsonNode> -> {
                            calculateFeatureValue(arguments, trackableFeatureValue)
                        }
                        else -> {
                            Mono.just(trackableFeatureValue)
                        }
                    }
                }
            }

            private fun calculateFeatureValue(
                arguments: ImmutableMap<GQLOperationPath, Mono<JsonNode>>,
                trackableFeatureValue: TrackableValue.PlannedValue<JsonNode>,
            ): Mono<TrackableValue<JsonNode>> {
                return Flux.merge(materializeArgumentsForFeatureCalculationInput(arguments))
                    .reduce(
                        persistentMapOf<String, JsonNode>(),
                        PersistentMap<String, JsonNode>::plus
                    )
                    .flatMap { materializedArgs: PersistentMap<String, JsonNode> ->
                        logger.debug(
                            "calculate_feature_value: [ materialized_args: {} ]",
                            materializedArgs
                        )
                        transformerCallable.invoke(materializedArgs)
                    }
                    .map { jn: JsonNode ->
                        trackableFeatureValue.transitionToCalculated {
                            calculatedValue(jn)
                            calculatedTimestamp(Instant.now())
                        }
                    }
            }

            private fun materializeArgumentsForFeatureCalculationInput(
                arguments: ImmutableMap<GQLOperationPath, Mono<JsonNode>>
            ): Iterable<Mono<Pair<String, JsonNode>>> {
                return when {
                    transformerCallable.argumentsByName.size == 1 &&
                        DEFAULT_UNARY_TRANSFORMER_ARGUMENT_NAME in
                            transformerCallable.argumentsByName -> {
                        argumentsByPath
                            .asSequence()
                            .firstOrNone()
                            .map { (p: GQLOperationPath, a: GraphQLArgument) ->
                                pairFeatureFieldArgumentWithInputArgument(arguments, p, a).map {
                                    (featureArgName: String, argValue: JsonNode) ->
                                    DEFAULT_UNARY_TRANSFORMER_ARGUMENT_NAME to argValue
                                }
                            }
                            .fold(::emptyList, ::listOf)
                    }
                    else -> {
                        argumentsByPath
                            .asSequence()
                            .map { (p: GQLOperationPath, a: GraphQLArgument) ->
                                pairFeatureFieldArgumentWithInputArgument(arguments, p, a)
                            }
                            .asIterable()
                    }
                }
            }

            private fun pairFeatureFieldArgumentWithInputArgument(
                inputArguments: ImmutableMap<GQLOperationPath, Mono<JsonNode>>,
                argumentPath: GQLOperationPath,
                graphQLArgument: GraphQLArgument,
            ): Mono<Pair<String, JsonNode>> {
                logger.debug(
                    """pair_feature_field_argument_with_input_argument: 
                        |[ input_args.keys: {}, 
                        |arg_path: {}, 
                        |arg.name: {} ]"""
                        .flatten(),
                    inputArguments.keys,
                    argumentPath,
                    graphQLArgument.name
                )
                return when (argumentPath) {
                    in inputArguments -> {
                        inputArguments[argumentPath]!!.map { jv: JsonNode ->
                            graphQLArgument.name to jv
                        }
                    }
                    else -> {
                        if (graphQLArgument.hasSetDefaultValue()) {
                            graphQLArgument.argumentDefaultValue
                                .toOption()
                                .map(InputValueWithState::getValue)
                                .filterIsInstance<Value<*>>()
                                .flatMap { v: Value<*> ->
                                    GraphQLValueToJsonNodeConverter.invoke(v)
                                }
                                .successIfDefined {
                                    ServiceError.of(
                                        """unable to determine default argument value for 
                                        |[ argument.name: %s ] for feature 
                                        |[ field_definition.name: %s ]"""
                                            .flatten(),
                                        graphQLArgument.name,
                                        featureGraphQLFieldDefinition.name
                                    )
                                }
                                .toMono()
                                .map { jn: JsonNode -> graphQLArgument.name to jn }
                        } else {
                            Mono.error {
                                ServiceError.of(
                                    """no argument with name [ %s ] 
                                    |supplied for feature 
                                    |[ field_definition.name: %s ]"""
                                        .flatten(),
                                    graphQLArgument.name,
                                    featureGraphQLFieldDefinition.name
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun builder(): FeatureCalculatorCallable.Builder {
        return DefaultFeatureCalculatorCallableBuilder()
    }
}
