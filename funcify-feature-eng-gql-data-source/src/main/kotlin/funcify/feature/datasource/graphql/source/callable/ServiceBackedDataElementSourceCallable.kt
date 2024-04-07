package funcify.feature.datasource.graphql.source.callable

import arrow.core.filterIsInstance
import arrow.core.getOrElse
import arrow.core.getOrNone
import arrow.core.identity
import arrow.core.lastOrNone
import arrow.core.toOption
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import funcify.feature.datasource.graphql.ServiceBackedDataElementSource
import funcify.feature.error.ServiceError
import funcify.feature.naming.StandardNamingConventions
import funcify.feature.schema.dataelement.DataElementCallable
import funcify.feature.schema.dataelement.DomainSpecifiedDataElementSource
import funcify.feature.schema.document.GQLDocumentComposer
import funcify.feature.schema.document.GQLDocumentSpec
import funcify.feature.schema.document.GQLDocumentSpecFactory
import funcify.feature.schema.path.operation.AliasedFieldSegment
import funcify.feature.schema.path.operation.FieldSegment
import funcify.feature.schema.path.operation.FragmentSpreadSegment
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.path.operation.InlineFragmentSegment
import funcify.feature.schema.path.operation.SelectionSegment
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.foldIntoTry
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import funcify.feature.tools.extensions.TryExtensions.tryFold
import graphql.language.AstPrinter
import graphql.language.Document
import graphql.language.OperationDefinition
import graphql.language.Value
import graphql.schema.GraphQLArgument
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
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
        private const val DATA_KEY: String = "data"
        private const val ERRORS_KEY: String = "errors"
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
        // TODO: Handle case where more than one argument shares the same name, but there exist
        // multiple values for a given argument name
        // TODO: Consider whether calculated selection and argument paths should be memoized
        return selections
            .asSequence()
            .foldIntoTry(GQLDocumentSpecFactory.defaultFactory().builder()) {
                sb: GQLDocumentSpec.Builder,
                s: GQLOperationPath ->
                when {
                    !domainPath.isAncestorTo(s) -> {
                        throw createErrorForUnrelatedSelection(s)
                    }
                    else -> {
                        sb.addFieldPath(
                            GQLOperationPath.of {
                                appendSelections(
                                    s.selection
                                        .asSequence()
                                        .drop(domainPath.selection.size - 1)
                                        .toList()
                                )
                            }
                        )
                    }
                }
            }
            .flatMap { sb: GQLDocumentSpec.Builder ->
                domainSpecifiedDataElementSource
                    .findPathsForRequiredArgumentsForSelections(selections)
                    .asSequence()
                    .map { requiredArgPath: GQLOperationPath ->
                        arguments
                            .getOrNone(requiredArgPath)
                            .successIfDefined(
                                createErrorForMissingRequiredArgument(requiredArgPath)
                            )
                            .map { argVal: JsonNode -> requiredArgPath to argVal }
                    }
                    .tryFold(sb to persistentMapOf<String, JsonNode>()) {
                        (s: GQLDocumentSpec.Builder, vars: PersistentMap<String, JsonNode>),
                        (rap: GQLOperationPath, av: JsonNode) ->
                        domainSpecifiedDataElementSource.allArgumentsByPath
                            .getOrNone(rap)
                            .map { ga: GraphQLArgument ->
                                s.putArgumentPathForVariableName(
                                    ga.name,
                                    GQLOperationPath.of {
                                        appendSelections(
                                            rap.selection
                                                .asSequence()
                                                .drop(domainPath.selection.size - 1)
                                                .toList()
                                        )
                                        argument(ga.name)
                                    }
                                ) to vars.put(ga.name, av)
                            }
                            .getOrElse { s to vars }
                    }
            }
            .map { (sb: GQLDocumentSpec.Builder, vars: PersistentMap<String, JsonNode>) ->
                domainSpecifiedDataElementSource
                    .findPathsForOptionalArgumentsForSelections(selections)
                    .asSequence()
                    .map { optionalArgPath: GQLOperationPath ->
                        arguments.getOrNone(optionalArgPath).map { argVal: JsonNode ->
                            optionalArgPath to argVal
                        }
                    }
                    .flatMapOptions()
                    .fold(sb to vars) {
                        (s: GQLDocumentSpec.Builder, vs: PersistentMap<String, JsonNode>),
                        (oap: GQLOperationPath, av: JsonNode) ->
                        // TODO: Cacheable operation: Cache canonical to selection path mapping
                        domainSpecifiedDataElementSource.allArgumentsByPath
                            .getOrNone(oap)
                            .map { ga: GraphQLArgument ->
                                s.putArgumentPathForVariableName(
                                    ga.name,
                                    GQLOperationPath.of {
                                        appendSelections(
                                            oap.selection
                                                .asSequence()
                                                .drop(domainPath.selection.size - 1)
                                                .toList()
                                        )
                                        argument(ga.name)
                                    }
                                ) to vs.put(ga.name, av)
                            }
                            .getOrElse { s to vs }
                    }
            }
            .flatMap { (sb: GQLDocumentSpec.Builder, vars: PersistentMap<String, JsonNode>) ->
                GQLDocumentComposer.defaultComposer()
                    .composeDocumentFromSpecWithSchema(
                        spec = sb.build(),
                        graphQLSchema =
                            domainSpecifiedDataElementSource.domainDataElementSourceGraphQLSchema
                    )
                    .map { d: Document ->
                        logger.debug(
                            "create_and_dispatch_call_to_graphql_api_based_on_selections: [ vars: {}, document: {} ]",
                            vars,
                            AstPrinter.printAst(d)
                        )
                        serviceBackedDataElementSource.graphQLApiService
                            .executeSingleQuery(
                                query = AstPrinter.printAst(d),
                                variables = vars,
                                operationName =
                                    d.toOption()
                                        .mapNotNull(Document::getDefinitions)
                                        .getOrElse(::emptyList)
                                        .asSequence()
                                        .filterIsInstance<OperationDefinition>()
                                        .mapNotNull(OperationDefinition::getName)
                                        .filter(String::isNotBlank)
                                        .firstOrNull()
                            )
                            .flatMap { responseJson: JsonNode ->
                                separateDataAndErrorBlocks(responseJson)
                            }
                    }
            }
            .fold(::identity) { t: Throwable ->
                Mono.error {
                    when (t) {
                        is ServiceError -> {
                            t
                        }
                        else -> {
                            createErrorForUnhandledException(t)
                        }
                    }
                }
            }
    }

    private fun separateDataAndErrorBlocks(responseJson: JsonNode): Mono<out JsonNode> {
        return when {
            // Case 1: response has non-null data value and if it has any errors block, no errors
            // reported therein
            responseJson.has(DATA_KEY) &&
                !responseJson.path(DATA_KEY).isNull &&
                (!responseJson.has(ERRORS_KEY) ||
                    responseJson
                        .get(ERRORS_KEY)
                        .toOption()
                        .filterIsInstance<ArrayNode>()
                        .exists(ArrayNode::isEmpty)) -> {
                Mono.just(responseJson.get(DATA_KEY))
            }
            // Case 2: response has non-empty errors block
            responseJson.has(ERRORS_KEY) &&
                !responseJson
                    .get(ERRORS_KEY)
                    .toOption()
                    .filterIsInstance<ArrayNode>()
                    .exists(ArrayNode::isEmpty) -> {
                Mono.error {
                    ServiceError.of(
                        """response from [ source.name: %s, graphql_api_service.service_name: %s ] 
                            |for [ domain_path: %s ] 
                            |contains errors [ %s ]"""
                            .flatten(),
                        serviceBackedDataElementSource.name,
                        serviceBackedDataElementSource.graphQLApiService.serviceName,
                        domainPath,
                        responseJson.get(ERRORS_KEY)
                    )
                }
            }
            // Case 3: response has null data value and empty or absent errors block
            else -> {
                Mono.error {
                    ServiceError.of(
                        """response from [ source.name: %s, graphql_api_service.service_name: %s ] 
                            |for [ domain_path: %s ] 
                            |has null data value but no errors reported"""
                            .flatten(),
                        serviceBackedDataElementSource.name,
                        serviceBackedDataElementSource.graphQLApiService.serviceName,
                        domainPath
                    )
                }
            }
        }
    }

    private fun createErrorForUnrelatedSelection(selection: GQLOperationPath): ServiceError {
        return ServiceError.of(
            "selection [ %s ] does not fall under domain [ domain_path: %s ]",
            selection,
            domainPath
        )
    }

    private fun createErrorForMissingRequiredArgument(
        requiredArgumentPath: GQLOperationPath
    ): () -> ServiceError {
        return {
            ServiceError.of(
                """required argument [ path: %s, name: %s ] 
                |not provided in arguments to %s 
                |for data_element_source [ name: %s ]"""
                    .flatten(),
                requiredArgumentPath,
                domainSpecifiedDataElementSource.allArgumentsByPath
                    .getOrNone(requiredArgumentPath)
                    .map(GraphQLArgument::getName)
                    .orNull(),
                ServiceBackedDataElementSourceCallable::class.simpleName,
                serviceBackedDataElementSource.name
            )
        }
    }

    private fun createErrorForUnhandledException(t: Throwable): ServiceError {
        return ServiceError.builder()
            .message(
                "unable to create GraphQL query to call service [ data_element_source.name: %s ]",
                serviceBackedDataElementSource.name
            )
            .cause(t)
            .build()
    }
}
