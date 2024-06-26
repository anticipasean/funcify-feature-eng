package funcify.feature.file.source.callable

import arrow.core.filterIsInstance
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.error.ServiceError
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.feature.FeatureCalculatorCallable
import funcify.feature.schema.feature.FeatureSpecifiedFeatureCalculator
import funcify.feature.schema.json.GraphQLValueToJsonNodeConverter
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.tracking.TrackableValue
import funcify.feature.schema.transformer.TransformerCallable
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.language.Value
import graphql.schema.GraphQLArgument
import graphql.schema.InputValueWithState
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.plus
import org.slf4j.Logger
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

internal class DefaultFeatureCalculatorCallable(
    override val featureSpecifiedFeatureCalculator: FeatureSpecifiedFeatureCalculator,
    override val transformerCallable: TransformerCallable
) : FeatureCalculatorCallable {

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
        arguments: ImmutableMap<GQLOperationPath, Mono<out JsonNode>>,
    ): Mono<out TrackableValue<JsonNode>> {
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
        arguments: ImmutableMap<GQLOperationPath, Mono<out JsonNode>>,
        trackableFeatureValue: TrackableValue.PlannedValue<JsonNode>,
    ): Mono<TrackableValue<JsonNode>> {
        return Flux.merge(materializeArgumentsForFeatureCalculationInput(arguments))
            .reduce(persistentMapOf<String, JsonNode>(), PersistentMap<String, JsonNode>::plus)
            .flatMap { materializedArgs: PersistentMap<String, JsonNode> ->
                logger.debug("calculate_feature_value: [ materialized_args: {} ]", materializedArgs)
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
        arguments: ImmutableMap<GQLOperationPath, Mono<out JsonNode>>
    ): Iterable<Mono<out Pair<String, JsonNode>>> {
        return when {
            transformerCallable.argumentsByName.size == 1 &&
                DEFAULT_UNARY_TRANSFORMER_ARGUMENT_NAME in transformerCallable.argumentsByName -> {
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
        inputArguments: ImmutableMap<GQLOperationPath, Mono<out JsonNode>>,
        argumentPath: GQLOperationPath,
        graphQLArgument: GraphQLArgument,
    ): Mono<out Pair<String, JsonNode>> {
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
                inputArguments[argumentPath]!!.map { jv: JsonNode -> graphQLArgument.name to jv }
            }
            else -> {
                if (graphQLArgument.hasSetDefaultValue()) {
                    graphQLArgument.argumentDefaultValue
                        .toOption()
                        .map(InputValueWithState::getValue)
                        .filterIsInstance<Value<*>>()
                        .flatMap { v: Value<*> -> GraphQLValueToJsonNodeConverter.invoke(v) }
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
