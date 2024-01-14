package funcify.feature.datasource.graphql.source.callable

import arrow.core.Option
import arrow.core.lastOrNone
import com.fasterxml.jackson.databind.JsonNode
import funcify.feature.error.ServiceError
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.dataelement.DataElementCallable
import funcify.feature.schema.path.operation.AliasedFieldSegment
import funcify.feature.schema.path.operation.FieldSegment
import funcify.feature.schema.path.operation.FragmentSpreadSegment
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.path.operation.InlineFragmentSegment
import funcify.feature.schema.path.operation.SelectionSegment
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.language.Field
import graphql.language.Value
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLFieldDefinition
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import org.slf4j.Logger
import reactor.core.publisher.Mono

internal class SchemaOnlyDataElementSourceCallable(
    override val domainFieldCoordinates: FieldCoordinates,
    override val domainPath: GQLOperationPath,
    override val domainGraphQLFieldDefinition: GraphQLFieldDefinition,
    override val selections: ImmutableSet<GQLOperationPath>,
    private val selectedField: Option<Field>,
    private val directivePathSelections: ImmutableSet<GQLOperationPath>,
    private val directivePathSelectionsWithValues: ImmutableMap<GQLOperationPath, Value<*>>,
) : DataElementCallable {

    companion object {
        private val METHOD_TAG: String =
            StandardNamingConventions.SNAKE_CASE.deriveName(
                    SchemaOnlyDataElementSourceCallable::class.simpleName ?: "<NA>"
                )
                .qualifiedForm + ".invoke"
        private val logger: Logger = loggerFor<SchemaOnlyDataElementSourceCallable>()
    }

    override fun invoke(arguments: ImmutableMap<GQLOperationPath, JsonNode>): Mono<JsonNode> {
        logger.debug(
            "{}: [ arguments.key: {}, selections: {} ]",
            METHOD_TAG,
            arguments.keys.asSequence().joinToString(", ", "{ ", " }"),
            selections.joinToString(", ", "{ ", " }")
        )
        return Mono.fromCallable {
                when {
                    arguments.isEmpty() -> {
                        throw ServiceError.of(
                            "no arguments have been provided to %s instance responsible for [ domain_field_coordinates: %s ]",
                            SchemaOnlyDataElementSourceCallable::class.simpleName,
                            domainFieldCoordinates
                        )
                    }
                    else -> {
                        arguments
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
                                        .filter { fn: String ->
                                            fn == domainFieldCoordinates.fieldName
                                        }
                                        .isDefined()
                            }
                            .map { (_: GQLOperationPath, jn: JsonNode) -> jn }
                            .successIfDefined {
                                ServiceError.of(
                                    "unable to find value for path matching domain field name [ domain_field_coordinates: %s ]",
                                    domainFieldCoordinates
                                )
                            }
                            .orElseThrow()
                    }
                }
            }
            .cache()
    }
}
