package funcify.feature.datasource.graphql.source.callable

import arrow.core.filterIsInstance
import arrow.core.getOrNone
import arrow.core.lastOrNone
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.datasource.graphql.ServiceBackedDataElementSource
import funcify.feature.error.ServiceError
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.dataelement.DataElementCallable
import funcify.feature.schema.dataelement.DomainSpecifiedDataElementSource
import funcify.feature.schema.json.GraphQLValueToJsonNodeConverter
import funcify.feature.schema.path.operation.AliasedFieldSegment
import funcify.feature.schema.path.operation.FieldSegment
import funcify.feature.schema.path.operation.FragmentSpreadSegment
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.path.operation.InlineFragmentSegment
import funcify.feature.schema.path.operation.SelectionSegment
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import funcify.feature.tools.extensions.TryExtensions.tryFold
import graphql.language.Value
import graphql.schema.GraphQLArgument
import graphql.schema.InputValueWithState
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.plus
import org.slf4j.Logger
import reactor.core.publisher.Mono

internal class ServiceBackedDataElementSourceCallable(
    override val domainSpecifiedDataElementSource: DomainSpecifiedDataElementSource,
    private val serviceBackedDataElementSource: ServiceBackedDataElementSource,
    override val selections: ImmutableSet<GQLOperationPath>,
    private val directivePathSelections: ImmutableSet<GQLOperationPath>,
    private val directivePathSelectionsWithValues: ImmutableMap<GQLOperationPath, Value<*>>,
) : DataElementCallable {

    companion object {
        private val METHOD_TAG: String =
            StandardNamingConventions.SNAKE_CASE.deriveName(
                    ServiceBackedDataElementSourceCallable::class.simpleName ?: "<NA>"
                )
                .qualifiedForm + ".invoke"
        private val logger: Logger = loggerFor<ServiceBackedDataElementSourceCallable>()
    }

    override fun invoke(arguments: ImmutableMap<GQLOperationPath, JsonNode>): Mono<JsonNode> {
        logger.debug(
            "{}: [ arguments.key: {}, selections: {} ]",
            METHOD_TAG,
            arguments.keys.asSequence().joinToString(", ", "{ ", " }"),
            selections.joinToString(", ", "{ ", " }")
        )
        return Mono.defer {
                when {
                    arguments.isEmpty() -> {
                        Mono.error {
                            ServiceError.of(
                                "no arguments have been provided to %s instance responsible for [ domain_field_coordinates: %s ]",
                                ServiceBackedDataElementSourceCallable::class.simpleName,
                                domainFieldCoordinates
                            )
                        }
                    }
                    hasArgumentMatchingDomainFieldOfDataElementSource(arguments) -> {
                        extractArgumentValueMatchingDomainFieldOfDataElementSource(arguments)
                    }
                    else -> {
                        createAndDispatchCallToGraphQLApiBasedOnSelections(arguments)
                    }
                }
            }
            .cache()
    }

    private fun hasArgumentMatchingDomainFieldOfDataElementSource(
        arguments: ImmutableMap<GQLOperationPath, JsonNode>
    ): Boolean {
        return arguments
            .asSequence()
            .firstOrNone { (p: GQLOperationPath, _: JsonNode) ->
                p.refersToSelection() &&
                    p.selection
                        .lastOrNone()
                        .map { ss: SelectionSegment ->
                            when (ss) {
                                is AliasedFieldSegment -> {
                                    ss.fieldName
                                }
                                is FieldSegment -> {
                                    ss.fieldName
                                }
                                is FragmentSpreadSegment -> {
                                    ss.selectedField.fieldName
                                }
                                is InlineFragmentSegment -> {
                                    ss.selectedField.fieldName
                                }
                            }
                        }
                        .filter { fn: String -> fn == domainFieldCoordinates.fieldName }
                        .isDefined()
            }
            .isDefined()
    }

    private fun extractArgumentValueMatchingDomainFieldOfDataElementSource(
        arguments: ImmutableMap<GQLOperationPath, JsonNode>
    ): Mono<out JsonNode> {
        return arguments
            .asSequence()
            .firstOrNone { (p: GQLOperationPath, _: JsonNode) ->
                p.refersToSelection() &&
                    p.selection
                        .lastOrNone()
                        .map { ss: SelectionSegment ->
                            when (ss) {
                                is AliasedFieldSegment -> {
                                    ss.fieldName
                                }
                                is FieldSegment -> {
                                    ss.fieldName
                                }
                                is FragmentSpreadSegment -> {
                                    ss.selectedField.fieldName
                                }
                                is InlineFragmentSegment -> {
                                    ss.selectedField.fieldName
                                }
                            }
                        }
                        .filter { fn: String -> fn == domainFieldCoordinates.fieldName }
                        .isDefined()
            }
            .map { (_: GQLOperationPath, jn: JsonNode) -> jn }
            .successIfDefined {
                ServiceError.of(
                    "unable to find value for path matching domain field name [ domain_field_coordinates: %s ]",
                    domainFieldCoordinates
                )
            }
            .toMono()
    }

    private fun createAndDispatchCallToGraphQLApiBasedOnSelections(
        arguments: ImmutableMap<GQLOperationPath, JsonNode>
    ): Mono<out JsonNode> {
        return domainSpecifiedDataElementSource.allArgumentsByPath
            .asSequence()
            .map { (ap: GQLOperationPath, ga: GraphQLArgument) ->
                when {
                    ap in arguments -> {
                        arguments
                            .getOrNone(ap)
                            .map { av: JsonNode -> ga.name to av }
                            .successIfDefined {
                                ServiceError.of(
                                    "argument expected but not found at path [ path: %s ]",
                                    ap
                                )
                            }
                    }
                    ga.hasSetDefaultValue() -> {
                        ga.argumentDefaultValue
                            .toOption()
                            .filter(InputValueWithState::isLiteral)
                            .mapNotNull(InputValueWithState::getValue)
                            .filterIsInstance<Value<*>>()
                            .flatMap(GraphQLValueToJsonNodeConverter)
                            .map { av: JsonNode -> ga.name to av }
                            .successIfDefined {
                                ServiceError.of(
                                    "argument default value [ name: %s ] is not literal",
                                    ga.name
                                )
                            }
                    }
                    else -> {
                        Try.failure<Pair<String, JsonNode>> {
                            ServiceError.of(
                                """argument without default value [ name: %s ] 
                                |not provided for 
                                |domain data element callable [ domain_path: %s ]"""
                                    .flatten(),
                                ga.name,
                                domainSpecifiedDataElementSource.domainPath
                            )
                        }
                    }
                }
            }
            .tryFold(persistentMapOf<String, JsonNode>(), PersistentMap<String, JsonNode>::plus)
            .map { args: PersistentMap<String, JsonNode> -> Mono.empty<JsonNode>() }
            .orElseGet { Mono.empty() }
    }
}
