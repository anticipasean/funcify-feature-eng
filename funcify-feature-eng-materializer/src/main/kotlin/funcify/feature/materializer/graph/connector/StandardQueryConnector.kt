package funcify.feature.materializer.graph.connector

import arrow.core.*
import funcify.feature.error.ServiceError
import funcify.feature.graph.DirectedPersistentGraph
import funcify.feature.graph.line.DirectedLine
import funcify.feature.materializer.graph.MaterializationEdge
import funcify.feature.materializer.graph.component.QueryComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContext.ArgumentComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContext.FieldComponentContext
import funcify.feature.materializer.graph.context.StandardQuery
import funcify.feature.materializer.input.context.RawInputContext
import funcify.feature.materializer.model.MaterializationMetamodel
import funcify.feature.schema.dataelement.DataElementCallable
import funcify.feature.schema.dataelement.DomainSpecifiedDataElementSource
import funcify.feature.schema.document.GQLDocumentSpec
import funcify.feature.schema.feature.FeatureCalculator
import funcify.feature.schema.feature.FeatureCalculatorCallable
import funcify.feature.schema.feature.FeatureJsonValuePublisher
import funcify.feature.schema.feature.FeatureJsonValueStore
import funcify.feature.schema.feature.FeatureSpecifiedFeatureCalculator
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.transformer.TransformerCallable
import funcify.feature.schema.transformer.TransformerSpecifiedTransformerSource
import funcify.feature.tools.container.attempt.Try
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.sequence
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.StringExtensions.toCamelCase
import funcify.feature.tools.extensions.TryExtensions.foldIntoTry
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.introspection.Introspection
import graphql.language.Argument
import graphql.language.ArrayValue
import graphql.language.Document
import graphql.language.Field
import graphql.language.NullValue
import graphql.language.Value
import graphql.language.VariableReference
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLCompositeType
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLFieldsContainer
import graphql.schema.GraphQLTypeUtil
import graphql.schema.InputValueWithState
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import org.slf4j.Logger

/**
 * @author smccarron
 * @created 2023-08-05
 */
internal object StandardQueryConnector : RequestMaterializationGraphConnector<StandardQuery> {

    private val logger: Logger = loggerFor<StandardQueryConnector>()

    override fun connectArgument(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext,
    ): StandardQuery {
        logger.debug(
            "connect_argument: [ argument_component_context: { path: {}, coordinates: {}, canonical_path: {} } ]",
            argumentComponentContext.path,
            argumentComponentContext.fieldCoordinates,
            argumentComponentContext.canonicalPath
        )
        return when {
            argumentComponentContext.fieldCoordinates in
                connectorContext.materializationMetamodel.elementTypeCoordinates -> {
                throw ServiceError.of("no arguments are permitted on element_type fields")
            }
            connectorContext.materializationMetamodel.dataElementElementTypePath.isAncestorTo(
                argumentComponentContext.canonicalPath
            ) -> {
                connectDataElementArgument(connectorContext, argumentComponentContext)
            }
            connectorContext.materializationMetamodel.featureElementTypePath.isAncestorTo(
                argumentComponentContext.canonicalPath
            ) -> {
                connectFeatureArgument(connectorContext, argumentComponentContext)
            }
            connectorContext.materializationMetamodel.transformerElementTypePath.isAncestorTo(
                argumentComponentContext.canonicalPath
            ) -> {
                connectTransformerArgument(connectorContext, argumentComponentContext)
            }
            else -> {
                throw ServiceError.of(
                    "unable to identify element_type bucket for argument [ path: %s, canonical_path: %s ]",
                    argumentComponentContext.path,
                    argumentComponentContext.canonicalPath
                )
            }
        }
    }

    private fun connectDataElementArgument(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext
    ): StandardQuery {
        logger.debug("connect_data_element_argument: [ path: {} ]", argumentComponentContext.path)
        return when {
            // Case 1: Already connected
            connectorContext.requestGraph.edgesFromPoint(argumentComponentContext.path).any() -> {
                connectorContext
            }
            // Case 2: Domain Data Element Argument
            argumentComponentContext.fieldCoordinates in
                connectorContext.materializationMetamodel
                    .domainSpecifiedDataElementSourceByCoordinates -> {
                connectDomainDataElementFieldToArgument(connectorContext, argumentComponentContext)
            }
            // Case 3: Subdomain Data Element Argument
            else -> {
                connectSubdomainDataElementToArgument(connectorContext, argumentComponentContext)
            }
        }
    }

    private fun connectDomainDataElementFieldToArgument(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext
    ): StandardQuery {
        val fieldPath: GQLOperationPath =
            argumentComponentContext.path
                .getParentPath()
                .filter { pp: GQLOperationPath -> connectorContext.requestGraph.contains(pp) }
                .successIfDefined {
                    ServiceError.of(
                        """domain data_element source field has not 
                        |been defined in request_materialization_graph 
                        |for [ path: %s ]"""
                            .flatten(),
                        argumentComponentContext.path.getParentPath().orNull()
                    )
                }
                .orElseThrow()
        val e: MaterializationEdge =
            when {
                // Case 1: Raw input context contains entry for parent field or alias of parent
                // field
                connectorContext.requestGraph
                    .get(fieldPath)
                    .toOption()
                    .filterIsInstance<FieldComponentContext>()
                    .map(FieldComponentContext::fieldCoordinates)
                    .map(FieldCoordinates::getFieldName)
                    .filter(connectorContext.rawInputContextKeys::contains)
                    .orElse {
                        connectorContext.materializationMetamodel.aliasCoordinatesRegistry
                            .getAllAliasesForField(argumentComponentContext.fieldCoordinates)
                            .asSequence()
                            .filter(connectorContext.rawInputContextKeys::contains)
                            .firstOrNone()
                    }
                    .isDefined() -> {
                    MaterializationEdge.RAW_INPUT_VALUE_PROVIDED
                }
                // Case 2: Raw input context contains entry for field argument that is set to
                // its default argument value or alias thereof
                argumentComponentContext.argument.value
                    .toOption()
                    .filterNot(VariableReference::class::isInstance)
                    .and(
                        connectorContext.materializationMetamodel
                            .domainSpecifiedDataElementSourceByCoordinates
                            .getOrNone(argumentComponentContext.fieldCoordinates)
                            .map(DomainSpecifiedDataElementSource::domainArgumentsByName)
                            .flatMap { awdvn: ImmutableMap<String, GraphQLArgument> ->
                                awdvn.getOrNone(argumentComponentContext.argument.name).filter {
                                    ga: GraphQLArgument ->
                                    ga.hasSetDefaultValue()
                                }
                            }
                            .filter { ga: GraphQLArgument ->
                                argumentComponentContext.argument.value ==
                                    ga.argumentDefaultValue.value
                            }
                    )
                    .and(
                        argumentComponentContext.argument.name
                            .toOption()
                            .filter(connectorContext.rawInputContextKeys::contains)
                            .orElse {
                                connectorContext.materializationMetamodel.aliasCoordinatesRegistry
                                    .getAllAliasesForFieldArgument(
                                        argumentComponentContext.fieldArgumentLocation
                                    )
                                    .asSequence()
                                    .filter(connectorContext.rawInputContextKeys::contains)
                                    .firstOrNone()
                            }
                    )
                    .isDefined() -> {
                    MaterializationEdge.RAW_INPUT_VALUE_PROVIDED
                }
                // Case 3: Variable key matches variable reference for argument
                argumentComponentContext.argument.value
                    .toOption()
                    .filterIsInstance<VariableReference>()
                    .mapNotNull(VariableReference::getName)
                    .filter(connectorContext.variableKeys::contains)
                    .isDefined() -> {
                    MaterializationEdge.VARIABLE_VALUE_PROVIDED
                }
                // Case 4: Argument value is not a variable reference, argument is not one with
                // default values, and argument value provided
                // Assumes validation function has asserted argument.value is of expected type
                // for
                // argument
                argumentComponentContext.argument.value
                    .toOption()
                    .filterNot(VariableReference::class::isInstance)
                    .and(
                        connectorContext.materializationMetamodel
                            .domainSpecifiedDataElementSourceByCoordinates
                            .getOrNone(argumentComponentContext.fieldCoordinates)
                            .map(
                                DomainSpecifiedDataElementSource::
                                    domainArgumentsWithoutDefaultValuesByName
                            )
                            .filter { awdvn: ImmutableMap<String, GraphQLArgument> ->
                                awdvn.containsKey(argumentComponentContext.argument.name)
                            }
                    )
                    .isDefined() -> {
                    MaterializationEdge.DIRECT_ARGUMENT_VALUE_PROVIDED
                }
                // Case 5: Argument value is not a variable reference, argument is one with
                // default values, and argument value provided that is not the default value
                // Assumes validation function has asserted argument.value is of expected type
                // for
                // argument
                argumentComponentContext.argument.value
                    .toOption()
                    .filterNot(VariableReference::class::isInstance)
                    .and(
                        connectorContext.materializationMetamodel
                            .domainSpecifiedDataElementSourceByCoordinates
                            .getOrNone(argumentComponentContext.fieldCoordinates)
                            .map(
                                DomainSpecifiedDataElementSource::
                                    domainArgumentsWithDefaultValuesByName
                            )
                            .flatMap { awdvn: ImmutableMap<String, GraphQLArgument> ->
                                awdvn.getOrNone(argumentComponentContext.argument.name)
                            }
                            .filter { ga: GraphQLArgument ->
                                argumentComponentContext.argument.value !=
                                    ga.argumentDefaultValue.value
                            }
                    )
                    .isDefined() -> {
                    MaterializationEdge.DIRECT_ARGUMENT_VALUE_PROVIDED
                }
                // Case 6: Argument value is not a variable reference and default argument value
                // provided for argument
                argumentComponentContext.argument.value
                    .toOption()
                    .filterNot(VariableReference::class::isInstance)
                    .and(
                        connectorContext.materializationMetamodel
                            .domainSpecifiedDataElementSourceByCoordinates
                            .getOrNone(argumentComponentContext.fieldCoordinates)
                            .map(
                                DomainSpecifiedDataElementSource::
                                    domainArgumentsWithDefaultValuesByName
                            )
                            .filter { awodvn: ImmutableMap<String, GraphQLArgument> ->
                                awodvn.containsKey(argumentComponentContext.argument.name)
                            }
                    )
                    .isDefined() -> {
                    MaterializationEdge.DEFAULT_ARGUMENT_VALUE_PROVIDED
                }
                else -> {
                    throw ServiceError.of(
                        "edge type could not be determined for [ field_path %s to argument_path %s ]",
                        fieldPath,
                        argumentComponentContext.path
                    )
                }
            }
        // Connect the field to its argument: field is dependent on argument
        // Connect the argument to its element type
        return connectorContext.update {
            requestGraph(
                connectorContext.requestGraph
                    .put(argumentComponentContext.path, argumentComponentContext)
                    .putEdge(fieldPath, argumentComponentContext.path, e)
                    .putEdge(
                        argumentComponentContext.path,
                        connectorContext.materializationMetamodel.dataElementElementTypePath,
                        MaterializationEdge.ELEMENT_TYPE
                    )
            )
            putConnectedPathForCanonicalPath(
                argumentComponentContext.canonicalPath,
                argumentComponentContext.path
            )
        }
    }

    private fun connectSubdomainDataElementToArgument(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext
    ): StandardQuery {
        logger.debug(
            "connect_subdomain_data_element_to_argument: [ argument_component_context.path: {} ]",
            argumentComponentContext.path
        )
        return when {
            // Case 1: Subdomain data element is already been connected to this argument
            argumentComponentContext.path.getParentPath().exists { fieldPath: GQLOperationPath ->
                connectorContext.requestGraph.containsEdge(fieldPath, argumentComponentContext.path)
            } -> {
                connectorContext
            }
            // Case 2: Subdomain data element belongs to a raw input data source
            rawInputSourcedDomainDataElementFieldForSubdomainDataElementArgument(
                    connectorContext,
                    argumentComponentContext
                )
                .isDefined() -> {
                connectSubdomainDataElementFieldToRawInputSourcedArgument(
                    connectorContext,
                    argumentComponentContext
                )
            }
            else -> {
                // Case 3: Subdomain data element argument value must be sourced from variable, be
                // default, or be provided directly in query
                connectSubdomainDataElementToVariableOrDirectlyProvidedArgument(
                    connectorContext,
                    argumentComponentContext
                )
            }
        }
    }

    private fun rawInputSourcedDomainDataElementFieldForSubdomainDataElementArgument(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext,
    ): Option<GQLOperationPath> {
        return argumentComponentContext.path
            .getParentPath()
            .filter { fieldPath: GQLOperationPath ->
                connectorContext.requestGraph.contains(fieldPath) &&
                    !connectorContext.dataElementCallableBuildersByPath.containsKey(fieldPath)
            }
            .flatMap { fieldPath: GQLOperationPath ->
                connectorContext.requestGraph.successorVertices(fieldPath).firstOrNone()
            }
            .filter { (domainPath: GQLOperationPath, _) ->
                connectorContext.dataElementCallableBuildersByPath.containsKey(domainPath) &&
                    connectorContext.requestGraph.edgesFromPoint(domainPath).any {
                        (dl: DirectedLine<GQLOperationPath>, e: MaterializationEdge) ->
                        dl.destinationPoint.refersToArgument() &&
                            e == MaterializationEdge.RAW_INPUT_VALUE_PROVIDED
                    }
            }
            .map { (domainPath: GQLOperationPath, _) -> domainPath }
    }

    private fun connectSubdomainDataElementFieldToRawInputSourcedArgument(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext,
    ): StandardQuery {
        val parentFieldPath: GQLOperationPath =
            argumentComponentContext.path
                .getParentPath()
                .filter { p: GQLOperationPath -> connectorContext.requestGraph.contains(p) }
                .successIfDefined {
                    ServiceError.of(
                        """parent data_element_field to argument [ path: %s ] 
                            |expected but not found in request_materialization_graph"""
                            .flatten(),
                        argumentComponentContext.path,
                    )
                }
                .orElseThrow()
        val domainPath: GQLOperationPath =
            rawInputSourcedDomainDataElementFieldForSubdomainDataElementArgument(
                    connectorContext,
                    argumentComponentContext
                )
                .successIfDefined {
                    ServiceError.of(
                        """domain_data_element with raw_input_source 
                        |expected but not found for 
                        |subdomain_data_element_field [ path: %s ]"""
                            .flatten(),
                        argumentComponentContext.path.getParentPath().orNull()
                    )
                }
                .orElseThrow()
        return when {
            argumentIsDefaultOrMissingValueForDataElement(
                connectorContext,
                argumentComponentContext
            ) -> {
                connectorContext.update {
                    // Make both subdomain field path and domain field path connect to argument path
                    requestGraph(
                        connectorContext.requestGraph
                            .put(argumentComponentContext.path, argumentComponentContext)
                            .putEdge(
                                parentFieldPath,
                                argumentComponentContext.path,
                                MaterializationEdge.DEFAULT_ARGUMENT_VALUE_PROVIDED
                            )
                            .putEdge(
                                domainPath,
                                argumentComponentContext.path,
                                MaterializationEdge.DEFAULT_ARGUMENT_VALUE_PROVIDED
                            )
                    )
                    putConnectedPathForCanonicalPath(
                        argumentComponentContext.canonicalPath,
                        argumentComponentContext.path
                    )
                }
            }
            else -> {
                connectorContext.update {
                    // Make both subdomain field path and domain field path connect to argument path
                    requestGraph(
                        connectorContext.requestGraph
                            .put(argumentComponentContext.path, argumentComponentContext)
                            .putEdge(
                                parentFieldPath,
                                argumentComponentContext.path,
                                MaterializationEdge.RAW_INPUT_VALUE_PROVIDED
                            )
                            .putEdge(
                                domainPath,
                                argumentComponentContext.path,
                                MaterializationEdge.RAW_INPUT_VALUE_PROVIDED
                            )
                    )
                    putConnectedPathForCanonicalPath(
                        argumentComponentContext.canonicalPath,
                        argumentComponentContext.path
                    )
                }
            }
        }
    }

    private fun argumentIsDefaultOrMissingValueForDataElement(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext
    ): Boolean {
        return connectorContext.materializationMetamodel.dataElementElementTypePath.isAncestorTo(
            argumentComponentContext.canonicalPath
        ) &&
            Try.attemptNullable {
                    connectorContext.materializationMetamodel.materializationGraphQLSchema
                        .getFieldDefinition(argumentComponentContext.fieldCoordinates)
                        .toOption()
                        .mapNotNull { gfd: GraphQLFieldDefinition ->
                            gfd.getArgument(argumentComponentContext.argument.name)
                        }
                        .orNull()
                }
                .orElseGet(::none)
                .flatMap { ga: GraphQLArgument ->
                    useArgumentInArgumentComponentContextIfValueIsDefaultForGraphQLArgument(
                        argumentComponentContext,
                        ga
                    )
                }
                .isDefined()
    }

    private fun connectSubdomainDataElementToVariableOrDirectlyProvidedArgument(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext,
    ): StandardQuery {
        val parentFieldPath: GQLOperationPath =
            argumentComponentContext.path
                .getParentPath()
                .filter { p: GQLOperationPath -> connectorContext.requestGraph.contains(p) }
                .successIfDefined {
                    ServiceError.of(
                        """parent field to data_element_argument [ path: %s ] 
                            |expected but not found in 
                            |request_materialization_graph"""
                            .flatten()
                    )
                }
                .orElseThrow()
        val domainDataElementPath: GQLOperationPath =
            connectorContext.requestGraph
                .successorVertices(parentFieldPath)
                .firstOrNone()
                .filter { (p: GQLOperationPath, _) ->
                    connectorContext.requestGraph.contains(p) &&
                        p in connectorContext.dataElementCallableBuildersByPath
                }
                .map { (p: GQLOperationPath, _) -> p }
                .successIfDefined {
                    ServiceError.of(
                        """domain data_element field for argument [ path: %s ] 
                        |expected but not found in 
                        |request_materialization_graph"""
                            .flatten(),
                        argumentComponentContext.path
                    )
                }
                .orElseThrow()
        val edge: MaterializationEdge =
            when {
                argumentComponentContext.argument.value is VariableReference -> {
                    MaterializationEdge.VARIABLE_VALUE_PROVIDED
                }
                argumentIsDefaultOrMissingValueForDataElement(
                    connectorContext,
                    argumentComponentContext
                ) -> {
                    MaterializationEdge.DEFAULT_ARGUMENT_VALUE_PROVIDED
                }
                argumentComponentContext.argument.value != null -> {
                    MaterializationEdge.DIRECT_ARGUMENT_VALUE_PROVIDED
                }
                else -> {
                    throw ServiceError.of(
                        "connection from subdomain data_element field to argument [ path: %s ] could not be determined",
                        argumentComponentContext.path
                    )
                }
            }
        return connectorContext.update {
            requestGraph(
                connectorContext.requestGraph
                    .put(argumentComponentContext.path, argumentComponentContext)
                    .putEdge(parentFieldPath, argumentComponentContext.path, edge)
                    .putEdge(domainDataElementPath, argumentComponentContext.path, edge)
            )
            putConnectedPathForCanonicalPath(
                argumentComponentContext.canonicalPath,
                argumentComponentContext.path
            )
        }
    }

    private fun connectFeatureArgument(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext
    ): StandardQuery {
        logger.debug("connect_feature_argument: [ path: {} ]", argumentComponentContext.path)
        return when {
            // Case 1: Already connected to data_element_field
            connectorContext.requestGraph.edgesFromPoint(argumentComponentContext.path).any() -> {
                connectorContext
            }
            // Case 2: Argument refers to variable
            argumentComponentContext.argument.value is VariableReference -> {
                connectFeatureFieldToVariableProvidedArgument(
                    connectorContext,
                    argumentComponentContext
                )
            }
            // Case 3: Argument set to default (or "missing") value but should be assessed
            // for possible wiring to data element
            argumentIsDefaultOrMissingValueForFeature(
                connectorContext,
                argumentComponentContext
            ) -> {
                connectFeatureArgumentToDataElementField(connectorContext, argumentComponentContext)
            }
            else -> {
                // Case 4: Argument set to non-default value: direct argument value
                connectFeatureArgumentToDirectArgumentValue(
                    connectorContext,
                    argumentComponentContext
                )
            }
        }
    }

    private fun connectFeatureFieldToVariableProvidedArgument(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext
    ): StandardQuery {
        val fieldPath: GQLOperationPath =
            argumentComponentContext.path
                .getParentPath()
                .filter { p: GQLOperationPath -> connectorContext.requestGraph.contains(p) }
                .successIfDefined {
                    ServiceError.of(
                        "feature_argument [ %s ] parent field path [ %s ] not found in request_materialization_graph",
                        argumentComponentContext.path,
                        argumentComponentContext.path.getParentPath().orNull()
                    )
                }
                .orElseThrow()
        return connectorContext.update {
            requestGraph(
                connectorContext.requestGraph
                    .put(argumentComponentContext.path, argumentComponentContext)
                    .putEdge(
                        fieldPath,
                        argumentComponentContext.path,
                        MaterializationEdge.VARIABLE_VALUE_PROVIDED
                    )
            )
            putConnectedPathForCanonicalPath(
                argumentComponentContext.canonicalPath,
                argumentComponentContext.path
            )
        }
    }

    private fun argumentIsDefaultOrMissingValueForFeature(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext
    ): Boolean {
        return connectorContext.materializationMetamodel
            .featureSpecifiedFeatureCalculatorsByCoordinates
            .getOrNone(argumentComponentContext.fieldCoordinates)
            .flatMap { fsfc: FeatureSpecifiedFeatureCalculator ->
                fsfc.argumentsByName.getOrNone(argumentComponentContext.argument.name)
            }
            .flatMap { ga: GraphQLArgument ->
                useArgumentInArgumentComponentContextIfValueIsDefaultForGraphQLArgument(
                    argumentComponentContext,
                    ga
                )
            }
            .isDefined()
    }

    private fun useArgumentInArgumentComponentContextIfValueIsDefaultForGraphQLArgument(
        argumentComponentContext: ArgumentComponentContext,
        graphQLArgument: GraphQLArgument,
    ): Option<Argument> {
        // case 1: arg default value same as this arg value
        return graphQLArgument.argumentDefaultValue
            .toOption()
            .filter(InputValueWithState::isLiteral)
            .filter(InputValueWithState::isSet)
            .mapNotNull(InputValueWithState::getValue)
            .filterIsInstance<Value<*>>()
            .flatMap { v: Value<*> ->
                argumentComponentContext.argument.toOption().filter { a: Argument -> v == a.value }
            }
            .orElse {
                // case 2: arg type is nullable and this arg value is null
                graphQLArgument.type
                    .toOption()
                    .filter(GraphQLTypeUtil::isNullable)
                    .and(
                        argumentComponentContext.argument.toOption().filter { a: Argument ->
                            a.value == null || a.value is NullValue
                        }
                    )
            }
            .orElse {
                // case 3: arg type is list of nullables and this arg value is null or empty
                // list value
                graphQLArgument.type
                    .toOption()
                    .mapNotNull(GraphQLTypeUtil::unwrapNonNull)
                    .filter(GraphQLTypeUtil::isList)
                    .and(
                        argumentComponentContext.argument.toOption().filter { a: Argument ->
                            a.value == null ||
                                a.value
                                    .toOption()
                                    .filterIsInstance<ArrayValue>()
                                    .mapNotNull(ArrayValue::getValues)
                                    .filter(List<Value<*>>::isEmpty)
                                    .isDefined()
                        }
                    )
            }
    }

    private fun connectFeatureArgumentToDataElementField(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext
    ): StandardQuery {
        logger.debug(
            "connect_feature_argument_to_data_element_field: [ path: {} ]",
            argumentComponentContext.path
        )
        return when {
            getCoordinatesOfConnectedDataElementOrFeatureFieldsMatchingFeatureArgument(
                    connectorContext,
                    argumentComponentContext
                )
                .isDefined() -> {
                connectFeatureFieldArgumentToDataElementFieldOnAlreadyConnectedDomainDataElementSourceOrFeatureField(
                    connectorContext,
                    argumentComponentContext
                )
            }
            getCoordinatesOfUnconnectedFeatureFieldMatchingFeatureArgument(
                    connectorContext,
                    argumentComponentContext
                )
                .isDefined() -> {
                connectFeatureFieldArgumentToUnconnectedFeatureField(
                    connectorContext,
                    argumentComponentContext
                )
            }
            getFirstCoordinatesToDataElementFieldPathsWithoutAlternativesOfConnectedDataElementSourceMatchingFeatureArgument(
                    connectorContext,
                    argumentComponentContext
                )
                .isDefined() -> {
                connectFeatureFieldArgumentToUnconnectedDataElementFieldsWithoutAlternativesOnConnectedDataElementSource(
                    connectorContext,
                    argumentComponentContext
                )
            }
            getFirstCoordinatesToDataElementFieldPathsWithAlternativesOfConnectedDataElementSourceMatchingFeatureArgument(
                    connectorContext,
                    argumentComponentContext
                )
                .isDefined() -> {
                connectFeatureFieldArgumentToUnconnectedDataElementFieldsWithAlternativesOnConnectedDataElementSource(
                    connectorContext,
                    argumentComponentContext
                )
            }
            getFirstCoordinatesToUnconnectedRawInputDataElementSourcePathPairsOfUnconnectedDataElementFieldMatchingFeatureArgument(
                    connectorContext,
                    argumentComponentContext
                )
                .isDefined() -> {
                connectFeatureFieldArgumentToDataElementFieldOnUnconnectedRawInputDataElementSource(
                    connectorContext,
                    argumentComponentContext
                )
            }
            getFirstCoordinatesToDataElementFieldsOnUnconnectedDataElementSourceWithCompleteVariableInputSetMatchingFeatureArgument(
                    connectorContext,
                    argumentComponentContext
                )
                .isDefined() -> {
                connectFeatureFieldArgumentToDataElementFieldsOnUnconnectedDataElementSourceWithCompleteVariableInputSet(
                    connectorContext,
                    argumentComponentContext
                )
            }
            else -> {
                throw ServiceError.of(
                    """unable to map feature_argument 
                    |[ argument: { path: %s, name: %s } ] 
                    |to data_element_field value"""
                        .flatten(),
                    argumentComponentContext.path,
                    argumentComponentContext.argument.name
                )
            }
        }
    }

    private fun getCoordinatesOfConnectedDataElementOrFeatureFieldsMatchingFeatureArgument(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext,
    ): Option<ImmutableSet<FieldCoordinates>> {
        return argumentComponentContext
            .toOption()
            .filter { acc: ArgumentComponentContext ->
                // Assert argument is for feature
                connectorContext.materializationMetamodel
                    .featureSpecifiedFeatureCalculatorsByCoordinates
                    .containsKey(acc.fieldCoordinates)
            }
            .map(ArgumentComponentContext::argument)
            .map(Argument::getName)
            .flatMap { n: String ->
                connectorContext.materializationMetamodel.dataElementFieldCoordinatesByFieldName
                    .getOrNone(n)
                    .fold(::emptySequence, ImmutableSet<FieldCoordinates>::asSequence)
                    .filter(connectorContext.connectedFieldPathsByCoordinates::containsKey)
                    .toPersistentSet()
                    .toOption()
                    .filter(ImmutableSet<FieldCoordinates>::isNotEmpty)
                    .orElse {
                        connectorContext.materializationMetamodel.featureCoordinatesByName
                            .getOrNone(n)
                            .filter { fc: FieldCoordinates ->
                                argumentComponentContext.fieldCoordinates != fc &&
                                    connectorContext.connectedFieldPathsByCoordinates.containsKey(
                                        fc
                                    )
                            }
                            .map(::persistentSetOf)
                    }
                    .orElse {
                        connectorContext.materializationMetamodel.aliasCoordinatesRegistry
                            .getFieldsWithAlias(n)
                            .asSequence()
                            .filter { fc: FieldCoordinates ->
                                argumentComponentContext.fieldCoordinates != fc
                            }
                            .filter(connectorContext.connectedFieldPathsByCoordinates::containsKey)
                            .toPersistentSet()
                            .toOption()
                            .filter(ImmutableSet<FieldCoordinates>::isNotEmpty)
                    }
            }
    }

    private fun connectFeatureFieldArgumentToDataElementFieldOnAlreadyConnectedDomainDataElementSourceOrFeatureField(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext
    ): StandardQuery {
        val fieldPath: GQLOperationPath =
            argumentComponentContext.path
                .getParentPath()
                .filter { p: GQLOperationPath -> connectorContext.requestGraph.contains(p) }
                .successIfDefined {
                    ServiceError.of(
                        "feature_argument [ argument.name: %s ] parent field path [ %s ] not found in request_materialization_graph",
                        argumentComponentContext.argument.name,
                        argumentComponentContext.path.getParentPath().orNull()
                    )
                }
                .orElseThrow()
        val coordinatesOfConnectedField: ImmutableSet<FieldCoordinates> =
            getCoordinatesOfConnectedDataElementOrFeatureFieldsMatchingFeatureArgument(
                    connectorContext,
                    argumentComponentContext
                )
                .successIfDefined {
                    ServiceError.of(
                        "no coordinates found for connected data element or feature matching feature argument [ path: %s ]",
                        argumentComponentContext.path
                    )
                }
                .orElseThrow()
        return connectorContext.update {
            requestGraph(
                coordinatesOfConnectedField
                    .asSequence()
                    .flatMap { fc: FieldCoordinates ->
                        connectorContext.connectedFieldPathsByCoordinates
                            .getOrNone(fc)
                            .fold(::emptySequence, ImmutableSet<GQLOperationPath>::asSequence)
                    }
                    .fold(
                        connectorContext.requestGraph
                            .put(argumentComponentContext.path, argumentComponentContext)
                            .putEdge(
                                fieldPath,
                                argumentComponentContext.path,
                                MaterializationEdge.EXTRACT_FROM_SOURCE
                            )
                    ) {
                        d:
                            DirectedPersistentGraph<
                                GQLOperationPath,
                                QueryComponentContext,
                                MaterializationEdge
                            >,
                        p: GQLOperationPath ->
                        d.putEdge(
                            argumentComponentContext.path,
                            p,
                            MaterializationEdge.EXTRACT_FROM_SOURCE
                        )
                    }
            )
            putConnectedPathForCanonicalPath(
                argumentComponentContext.canonicalPath,
                argumentComponentContext.path
            )
        }
    }

    private fun getCoordinatesOfUnconnectedFeatureFieldMatchingFeatureArgument(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext
    ): Option<FieldCoordinates> {
        return argumentComponentContext
            .toOption()
            .filter { acc: ArgumentComponentContext ->
                // Assert argument belongs to feature
                connectorContext.materializationMetamodel
                    .featureSpecifiedFeatureCalculatorsByCoordinates
                    .containsKey(acc.fieldCoordinates)
            }
            .and(
                connectorContext.materializationMetamodel.featureCoordinatesByName
                    .getOrNone(argumentComponentContext.argument.name)
                    .filter { fc: FieldCoordinates ->
                        // Assert this argument's feature is not the same as the one to which it may
                        // be wired
                        argumentComponentContext.fieldCoordinates != fc &&
                            !connectorContext.connectedFieldPathsByCoordinates.containsKey(fc)
                    }
            )
            .orElse {
                connectorContext.materializationMetamodel.aliasCoordinatesRegistry
                    .getFieldsWithAlias(argumentComponentContext.argument.name)
                    .asSequence()
                    .filter { fc: FieldCoordinates ->
                        // Assert that these coordinates belong to a feature and that these
                        // coordinates don't point to the same feature that this feature's argument
                        // will be wired to
                        connectorContext.materializationMetamodel
                            .featureSpecifiedFeatureCalculatorsByCoordinates
                            .containsKey(fc) &&
                            argumentComponentContext.fieldCoordinates != fc &&
                            !connectorContext.connectedFieldPathsByCoordinates.containsKey(fc)
                    }
                    .firstOrNone()
            }
    }

    private fun connectFeatureFieldArgumentToUnconnectedFeatureField(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext,
    ): StandardQuery {
        val fc: FieldCoordinates =
            getCoordinatesOfUnconnectedFeatureFieldMatchingFeatureArgument(
                    connectorContext,
                    argumentComponentContext
                )
                .successIfDefined {
                    ServiceError.of(
                        """corresponding feature_field_coordinates expected for 
                        |[ field_argument: { path: %s, argument.name: %s } ]"""
                            .flatten(),
                        argumentComponentContext.path,
                        argumentComponentContext.argument.name
                    )
                }
                .orElseThrow()
        val fsfc: FeatureSpecifiedFeatureCalculator =
            connectorContext.materializationMetamodel
                .featureSpecifiedFeatureCalculatorsByCoordinates
                .getOrNone(fc)
                .successIfDefined {
                    ServiceError.of(
                        "%s expected but not found in %s at coordinates: [ %s ]",
                        FeatureSpecifiedFeatureCalculator::class.simpleName,
                        MaterializationMetamodel::class.simpleName,
                        fc
                    )
                }
                .orElseThrow()
        val fieldPath: GQLOperationPath =
            argumentComponentContext.path
                .getParentPath()
                .filter { p: GQLOperationPath -> connectorContext.requestGraph.contains(p) }
                .successIfDefined {
                    ServiceError.of(
                        "feature_argument [ argument.name: %s ] to feature field path [ %s ] not found in request_materialization_graph",
                        argumentComponentContext.argument.name,
                        argumentComponentContext.path.getParentPath().orNull()
                    )
                }
                .orElseThrow()

        // Can start at gqlo:/features/__here__ since the feature element type path must have been
        // traversed to reach this feature_argument wiring point
        return Try.success(connectorContext)
            .map { ctx: StandardQuery ->
                // Connect parents
                ((connectorContext.materializationMetamodel.featureElementTypePath.selection.size +
                        1) until fsfc.featurePath.selection.size)
                    .asSequence()
                    .map { limit: Int ->
                        // Note: Does not start with possibly aliased or fragment-spread nested
                        // feature element_type paths
                        GQLOperationPath.of {
                            selections(fsfc.featurePath.selection.subList(0, limit))
                        }
                    }
                    .fold(ctx) { sq: StandardQuery, p: GQLOperationPath ->
                        logger.debug(
                            "feature_argument_parent calculated [ {} ] for feature_path: [ {} ]",
                            p,
                            fsfc.featurePath
                        )
                        when {
                            p in sq.connectedPathsByCanonicalPath -> {
                                sq
                            }
                            else -> {
                                connectorContext.materializationMetamodel.fieldCoordinatesByPath
                                    .getOrNone(p)
                                    .flatMap(ImmutableSet<FieldCoordinates>::firstOrNone)
                                    .map { fc: FieldCoordinates ->
                                        connectorContext.queryComponentContextFactory
                                            .fieldComponentContextBuilder()
                                            .field(Field.newField().name(fc.fieldName).build())
                                            .fieldCoordinates(fc)
                                            .path(p)
                                            .canonicalPath(p)
                                            .build()
                                    }
                                    .successIfDefined {
                                        ServiceError.of(
                                            "[ path: %s ] does not match known field_coordinates",
                                            p
                                        )
                                    }
                                    .map { sfcc: FieldComponentContext -> connectField(sq, sfcc) }
                                    .orElseThrow()
                            }
                        }
                    }
            }
            .map { ctx: StandardQuery ->
                // Connect feature field itself
                val featureFieldComponentContext: FieldComponentContext =
                    ctx.queryComponentContextFactory
                        .fieldComponentContextBuilder()
                        .field(Field.newField().name(fsfc.featureName).build())
                        .path(fsfc.featurePath)
                        .fieldCoordinates(fsfc.featureFieldCoordinates)
                        .canonicalPath(fsfc.featurePath)
                        .build()
                connectField(ctx, featureFieldComponentContext)
            }
            .map { ctx: StandardQuery ->
                // Connect feature field's arguments
                fsfc.argumentsByPath
                    .asSequence()
                    .map { (p: GQLOperationPath, a: GraphQLArgument) ->
                        connectorContext.queryComponentContextFactory
                            .argumentComponentContextBuilder()
                            .path(p)
                            .argument(
                                Argument.newArgument()
                                    .name(a.name)
                                    .value(
                                        a.toOption()
                                            .filter(GraphQLArgument::hasSetDefaultValue)
                                            .map { ga: GraphQLArgument -> ga.argumentDefaultValue }
                                            .filter(InputValueWithState::isLiteral)
                                            .mapNotNull(InputValueWithState::getValue)
                                            .filterIsInstance<Value<*>>()
                                            // TODO: Check whether null is permitted as default
                                            // value for argument
                                            .getOrElse { NullValue.of() }
                                    )
                                    .build()
                            )
                            .fieldCoordinates(fsfc.featureFieldCoordinates)
                            .canonicalPath(p)
                            .build()
                    }
                    .fold(ctx) { sq: StandardQuery, facc: ArgumentComponentContext ->
                        connectFeatureArgument(sq, facc)
                    }
            }
            .map { ctx: StandardQuery ->
                // Connect feature field to this feature argument
                ctx.update {
                    requestGraph(
                            ctx.requestGraph
                                .put(argumentComponentContext.path, argumentComponentContext)
                                .putEdge(
                                    fieldPath,
                                    argumentComponentContext.path,
                                    MaterializationEdge.EXTRACT_FROM_SOURCE
                                )
                        )
                        .putConnectedPathForCanonicalPath(
                            argumentComponentContext.canonicalPath,
                            argumentComponentContext.path
                        )
                }
            }
            .orElseThrow()
    }

    private fun getFirstCoordinatesToDataElementFieldPathsWithoutAlternativesOfConnectedDataElementSourceMatchingFeatureArgument(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext
    ): Option<Pair<FieldCoordinates, Set<GQLOperationPath>>> {
        return getDataElementFieldCoordinatesWithAliasMatchingArgumentName(
                connectorContext,
                argumentComponentContext
            )
            .orElse {
                connectorContext.materializationMetamodel.dataElementFieldCoordinatesByFieldName
                    .getOrNone(argumentComponentContext.argument.name)
                    .filter(ImmutableSet<FieldCoordinates>::isNotEmpty)
            }
            .fold(::emptySequence, ImmutableSet<FieldCoordinates>::asSequence)
            .filter { fc: FieldCoordinates ->
                connectorContext.materializationMetamodel.pathsByFieldCoordinates
                    .getOrNone(fc)
                    .filter { ps: ImmutableSet<GQLOperationPath> -> ps.size == 1 }
                    .isDefined()
            }
            .flatMap { fc: FieldCoordinates ->
                // TODO: Consider whether this operation is cacheable
                connectorContext.materializationMetamodel.pathsByFieldCoordinates
                    .getOrElse(fc, ::persistentSetOf)
                    .asSequence()
                    .filter { p: GQLOperationPath ->
                        !connectorContext.connectedPathsByCanonicalPath.containsKey(p)
                    }
                    .flatMap { p: GQLOperationPath ->
                        connectorContext.materializationMetamodel
                            .domainDataElementDataSourcePathByDescendentPath
                            .invoke(p)
                            .sequence()
                    }
                    .filter { dp: GQLOperationPath ->
                        connectorContext.connectedPathsByCanonicalPath.containsKey(dp)
                    }
                    .toSet()
                    .let { dps: Set<GQLOperationPath> ->
                        if (dps.isNotEmpty()) {
                            sequenceOf(fc to dps)
                        } else {
                            emptySequence()
                        }
                    }
            }
            .firstOrNone()
    }

    private fun connectFeatureFieldArgumentToUnconnectedDataElementFieldsWithoutAlternativesOnConnectedDataElementSource(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext
    ): StandardQuery {
        val (fc: FieldCoordinates, dsdesps: Set<GQLOperationPath>) =
            getFirstCoordinatesToDataElementFieldPathsWithoutAlternativesOfConnectedDataElementSourceMatchingFeatureArgument(
                    connectorContext,
                    argumentComponentContext
                )
                .successIfDefined {
                    ServiceError.of(
                        """first coordinates to connected data element source paths pair 
                        |expected but not found for matching argument [ path: %s ]"""
                            .flatten(),
                        argumentComponentContext.path
                    )
                }
                .orElseThrow()
        logger.debug(
            """connect_feature_field_argument_to_unconnected_data_element_field_
                |on_connected_data_element_source: 
                |[ data_element_field_coordinates: {}, 
                |connected_domain_data_element_paths: {} ]"""
                .flatten(),
            fc,
            dsdesps
        )
        val featureFieldPath: GQLOperationPath =
            argumentComponentContext.path
                .getParentPath()
                .filter { p: GQLOperationPath -> connectorContext.requestGraph.contains(p) }
                .successIfDefined {
                    ServiceError.of(
                        "feature_argument [ %s ] for feature field path [ %s ] not found in request_materialization_graph",
                        argumentComponentContext.path,
                        argumentComponentContext.path.getParentPath().orNull()
                    )
                }
                .orElseThrow()
        return dsdesps
            .asSequence()
            .foldIntoTry(connectorContext) {
                sq: StandardQuery,
                domainDataElementPath: GQLOperationPath ->
                connectFeatureArgumentToSpecificDataElementFieldWithinConnectedDomainDataElementSource(
                    sq,
                    domainDataElementPath,
                    fc,
                    argumentComponentContext,
                    featureFieldPath
                )
            }
            .orElseThrow()
    }

    private fun connectFeatureArgumentToSpecificDataElementFieldWithinConnectedDomainDataElementSource(
        connectorContext: StandardQuery,
        domainDataElementPath: GQLOperationPath,
        specificDataElementFieldCoordinates: FieldCoordinates,
        featureArgumentComponentContext: ArgumentComponentContext,
        featureFieldPath: GQLOperationPath,
    ): StandardQuery {
        val canonicalDomainDataElementPath: GQLOperationPath =
            connectorContext.canonicalPathByConnectedPath
                .getOrNone(domainDataElementPath)
                .successIfDefined {
                    ServiceError.of(
                        """domain_data_element_path [ path: %s ] 
                            |not connected within 
                            |request_materialization_graph"""
                            .flatten(),
                        domainDataElementPath
                    )
                }
                .orElseThrow()
        val childDataElementFieldCanonicalPath: GQLOperationPath =
            connectorContext.materializationMetamodel.firstPathWithFieldCoordinatesUnderPath
                .invoke(specificDataElementFieldCoordinates, canonicalDomainDataElementPath)
                .filter { p: GQLOperationPath ->
                    connectorContext.materializationMetamodel.dataElementElementTypePath
                        .isAncestorTo(p)
                }
                .successIfDefined {
                    ServiceError.of(
                        """path mapping to field_coordinates [ %s ] 
                        |under domain_data_element_source [ path: %s ] 
                        |expected but not found"""
                            .flatten(),
                        specificDataElementFieldCoordinates,
                        canonicalDomainDataElementPath
                    )
                }
                .orElseThrow()
        val childDataElementFieldPathToConnect: GQLOperationPath =
            domainDataElementPath.transform {
                appendSelections(
                    childDataElementFieldCanonicalPath.selection.subList(
                        domainDataElementPath.selection.size,
                        childDataElementFieldCanonicalPath.selection.size
                    )
                )
            }
        logger.debug(
            """connect_feature_argument_to_specific_data_element_field_
                |within_connected_domain_data_element_source: 
                |[ child_data_element_field_canonical_path: {}, 
                |child_data_element_field_path_to_connect: {} ]"""
                .flatten(),
            childDataElementFieldCanonicalPath,
            childDataElementFieldPathToConnect
        )
        return (domainDataElementPath.selection.size + 1)
            .rangeTo(childDataElementFieldCanonicalPath.selection.size)
            .asSequence()
            .foldIntoTry(connectorContext) { sq: StandardQuery, limit: Int ->
                val p: GQLOperationPath =
                    if (childDataElementFieldPathToConnect.selection.size == limit) {
                        childDataElementFieldPathToConnect
                    } else {
                        domainDataElementPath.transform {
                            appendSelections(
                                childDataElementFieldPathToConnect.selection.subList(
                                    domainDataElementPath.selection.size,
                                    limit
                                )
                            )
                        }
                    }
                when {
                    p in sq.canonicalPathByConnectedPath -> {
                        sq
                    }
                    else -> {
                        val cp: GQLOperationPath =
                            if (childDataElementFieldCanonicalPath.selection.size == limit) {
                                childDataElementFieldCanonicalPath
                            } else {
                                canonicalDomainDataElementPath.transform {
                                    appendSelections(
                                        childDataElementFieldCanonicalPath.selection.subList(
                                            canonicalDomainDataElementPath.selection.size,
                                            limit
                                        )
                                    )
                                }
                            }
                        sq.materializationMetamodel.fieldCoordinatesByPath
                            .getOrNone(cp)
                            .flatMap(ImmutableSet<FieldCoordinates>::firstOrNone)
                            .successIfDefined {
                                ServiceError.of(
                                    "[ path: %s ] does not match known field_coordinates",
                                    p
                                )
                            }
                            .flatMap { fc: FieldCoordinates ->
                                Try.attemptNullable({
                                        sq.materializationMetamodel.materializationGraphQLSchema
                                            .getFieldDefinition(fc)
                                    }) {
                                        ServiceError.of(
                                            "field_definition expected but not found for [ coordinates: %s ]",
                                            fc
                                        )
                                    }
                                    .mapFailure { t: Throwable ->
                                        ServiceError.builder()
                                            .message(
                                                "error occurred when looking up field_definition for [ coordinates: %s ]",
                                                fc
                                            )
                                            .cause(t)
                                            .build()
                                    }
                                    .map { gfd: GraphQLFieldDefinition -> fc to gfd }
                            }
                            .map { (fc: FieldCoordinates, gfd: GraphQLFieldDefinition) ->
                                sequenceOf(
                                        sq.queryComponentContextFactory
                                            .fieldComponentContextBuilder()
                                            .field(Field.newField().name(gfd.name).build())
                                            .fieldCoordinates(fc)
                                            .path(p)
                                            .canonicalPath(cp)
                                            .build()
                                    )
                                    .plus(
                                        createArgumentContextsForSubDomainDataElementField(
                                            standardQuery = sq,
                                            domainDataElementPath = domainDataElementPath,
                                            subdomainDataElementPath = p,
                                            subdomainCanonicalDataElementPath = cp,
                                            subdomainDataElementFieldCoordinates = fc,
                                            subdomainDataElementFieldDefinition = gfd
                                        )
                                    )
                            }
                            .orElseThrow()
                            .foldIntoTry(sq) { sq1: StandardQuery, qcc: QueryComponentContext ->
                                when (qcc) {
                                    is ArgumentComponentContext -> {
                                        connectArgument(sq1, qcc)
                                    }
                                    is FieldComponentContext -> {
                                        connectField(sq1, qcc)
                                    }
                                }
                            }
                            .orElseThrow()
                    }
                }
            }
            .map { sq: StandardQuery ->
                sq.update {
                    requestGraph(
                            sq.requestGraph
                                .put(
                                    featureArgumentComponentContext.path,
                                    featureArgumentComponentContext
                                )
                                .putEdge(
                                    featureFieldPath,
                                    featureArgumentComponentContext.path,
                                    MaterializationEdge.EXTRACT_FROM_SOURCE
                                )
                                .putEdge(
                                    featureArgumentComponentContext.path,
                                    childDataElementFieldCanonicalPath,
                                    MaterializationEdge.EXTRACT_FROM_SOURCE
                                )
                        )
                        .putConnectedPathForCanonicalPath(
                            featureArgumentComponentContext.canonicalPath,
                            featureArgumentComponentContext.path
                        )
                }
            }
            .orElseThrow()
    }

    private fun createArgumentContextsForSubDomainDataElementField(
        standardQuery: StandardQuery,
        domainDataElementPath: GQLOperationPath,
        subdomainDataElementPath: GQLOperationPath,
        subdomainCanonicalDataElementPath: GQLOperationPath,
        subdomainDataElementFieldCoordinates: FieldCoordinates,
        subdomainDataElementFieldDefinition: GraphQLFieldDefinition
    ): Sequence<ArgumentComponentContext> {
        return when {
            standardQuery.requestGraph.edgesFromPoint(domainDataElementPath).any {
                (_, edge: MaterializationEdge) ->
                edge == MaterializationEdge.RAW_INPUT_VALUE_PROVIDED
            } -> {
                subdomainDataElementFieldDefinition.arguments.asSequence().map { ga: GraphQLArgument
                    ->
                    Argument.newArgument()
                        .name(ga.name)
                        .value(
                            ga.argumentDefaultValue
                                .toOption()
                                .filter(InputValueWithState::isLiteral)
                                .filter(InputValueWithState::isSet)
                                .mapNotNull(InputValueWithState::getValue)
                                .filterIsInstance<Value<*>>()
                                .getOrElse { NullValue.of() }
                        )
                        .build()
                }
            }
            else -> {
                subdomainDataElementFieldDefinition.arguments.asSequence().flatMap {
                    ga: GraphQLArgument ->
                    standardQuery.materializationMetamodel.aliasCoordinatesRegistry
                        .getAllAliasesForFieldArgument(
                            subdomainDataElementFieldCoordinates,
                            ga.name
                        )
                        .asSequence()
                        .firstOrNone { argAlias: String -> argAlias in standardQuery.variableKeys }
                        .map { argAlias: String ->
                            Argument.newArgument()
                                .name(ga.name)
                                .value(
                                    VariableReference.newVariableReference().name(argAlias).build()
                                )
                                .build()
                        }
                        .orElse {
                            ga.name.toOption().filter(standardQuery.variableKeys::contains).map {
                                name: String ->
                                Argument.newArgument()
                                    .name(name)
                                    .value(
                                        VariableReference.newVariableReference().name(name).build()
                                    )
                                    .build()
                            }
                        }
                        .orElse {
                            ga.argumentDefaultValue
                                .toOption()
                                .filter(InputValueWithState::isLiteral)
                                .filter(InputValueWithState::isSet)
                                .mapNotNull(InputValueWithState::getValue)
                                .filterIsInstance<Value<*>>()
                                .map { v: Value<*> ->
                                    Argument.newArgument().name(ga.name).value(v).build()
                                }
                        }
                        .sequence()
                }
            }
        }.map { a: Argument ->
            standardQuery.queryComponentContextFactory
                .argumentComponentContextBuilder()
                .argument(a)
                .fieldCoordinates(subdomainDataElementFieldCoordinates)
                .path(subdomainDataElementPath.transform { argument(a.name) })
                .canonicalPath(subdomainCanonicalDataElementPath.transform { argument(a.name) })
                .build()
        }
    }

    private fun getFirstCoordinatesToDataElementFieldPathsWithAlternativesOfConnectedDataElementSourceMatchingFeatureArgument(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext
    ): Option<Pair<FieldCoordinates, Set<GQLOperationPath>>> {
        return getDataElementFieldCoordinatesWithAliasMatchingArgumentName(
                connectorContext,
                argumentComponentContext
            )
            .orElse {
                connectorContext.materializationMetamodel.dataElementFieldCoordinatesByFieldName
                    .getOrNone(argumentComponentContext.argument.name)
                    .filter(ImmutableSet<FieldCoordinates>::isNotEmpty)
            }
            .fold(::emptySequence, ImmutableSet<FieldCoordinates>::asSequence)
            .filter { fc: FieldCoordinates ->
                connectorContext.materializationMetamodel.pathsByFieldCoordinates
                    .getOrNone(fc)
                    .filter { ps: ImmutableSet<GQLOperationPath> -> ps.size > 1 }
                    .isDefined()
            }
            .flatMap { fc: FieldCoordinates ->
                // TODO: Consider whether this operation is cacheable
                connectorContext.materializationMetamodel.pathsByFieldCoordinates
                    .getOrElse(fc, ::persistentSetOf)
                    .asSequence()
                    .filter { p: GQLOperationPath ->
                        !connectorContext.connectedPathsByCanonicalPath.containsKey(p)
                    }
                    .flatMap { p: GQLOperationPath ->
                        connectorContext.materializationMetamodel
                            .domainDataElementDataSourcePathByDescendentPath
                            .invoke(p)
                            .sequence()
                    }
                    .filter { dp: GQLOperationPath ->
                        connectorContext.connectedPathsByCanonicalPath.containsKey(dp)
                    }
                    .toSet()
                    .let { dps: Set<GQLOperationPath> ->
                        if (dps.isNotEmpty()) {
                            sequenceOf(fc to dps)
                        } else {
                            emptySequence()
                        }
                    }
            }
            .firstOrNone()
    }

    private fun connectFeatureFieldArgumentToUnconnectedDataElementFieldsWithAlternativesOnConnectedDataElementSource(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext
    ): StandardQuery {
        val (
            dataElementCoordinates: FieldCoordinates,
            connectedDomainDataElementPaths: Set<GQLOperationPath>) =
            getFirstCoordinatesToDataElementFieldPathsWithAlternativesOfConnectedDataElementSourceMatchingFeatureArgument(
                    connectorContext,
                    argumentComponentContext
                )
                .successIfDefined {
                    ServiceError.of(
                        "coordinates to data_element field paths with alternatives not found for [ feature.argument.path: %s ]",
                        argumentComponentContext.path
                    )
                }
                .orElseThrow()
        val shortestCanonicalPathForCoordinates: GQLOperationPath =
            connectorContext.materializationMetamodel.pathsByFieldCoordinates
                .getOrElse(dataElementCoordinates, ::persistentSetOf)
                .minOrNull()
                .toOption()
                .successIfDefined {
                    ServiceError.of(
                        "shortest path not found for [ data_element_field_coordinates: %s ] when attempting to connect [ feature.argument.path: %s ]",
                        dataElementCoordinates,
                        argumentComponentContext.path
                    )
                }
                .orElseThrow()
        val featureFieldPath: GQLOperationPath =
            argumentComponentContext.path
                .getParentPath()
                .filter { p: GQLOperationPath -> connectorContext.requestGraph.contains(p) }
                .successIfDefined {
                    ServiceError.of(
                        "feature_argument [ %s ] for feature field path [ %s ] not found in request_materialization_graph",
                        argumentComponentContext.path,
                        argumentComponentContext.path.getParentPath().orNull()
                    )
                }
                .orElseThrow()
        logger.info(
            """connect_feature_field_argument_to_unconnected_data_element_fields_with_alternatives_
                |on_connected_data_element_source: 
                |[ data_element_coordinates: {}, shortest_canonical_path: {}, feature.argument.path: {} ]"""
                .flatten(),
            dataElementCoordinates,
            shortestCanonicalPathForCoordinates,
            argumentComponentContext.path
        )
        logger.debug(
            "domain_data_element_for_shortest_canonical_path: {}",
            connectorContext.materializationMetamodel
                .domainDataElementDataSourcePathByDescendentPath
                .invoke(shortestCanonicalPathForCoordinates)
        )
        logger.debug(
            "connected_domain_data_element_paths: [ {} ]",
            connectedDomainDataElementPaths.asSequence().joinToString(",\n")
        )
        return when {
            // Case 1: Domain data element source in which this data element field is canonical has
            // already been connected
            connectorContext.materializationMetamodel
                .domainDataElementDataSourcePathByDescendentPath
                .invoke(shortestCanonicalPathForCoordinates)
                .exists { dp: GQLOperationPath ->
                    connectorContext.connectedPathsByCanonicalPath
                        .getOrElse(dp, ::persistentSetOf)
                        .containsAll(connectedDomainDataElementPaths)
                } -> {
                connectorContext.materializationMetamodel
                    .domainDataElementDataSourcePathByDescendentPath
                    .invoke(shortestCanonicalPathForCoordinates)
                    .sequence()
                    .flatMap { dp: GQLOperationPath ->
                        connectorContext.connectedPathsByCanonicalPath
                            .getOrElse(dp, ::persistentSetOf)
                            .asSequence()
                    }
                    .foldIntoTry(connectorContext) {
                        sq: StandardQuery,
                        connectedDomainDataElementPath: GQLOperationPath ->
                        connectFeatureArgumentToSpecificDataElementFieldWithinConnectedDomainDataElementSource(
                            sq,
                            connectedDomainDataElementPath,
                            dataElementCoordinates,
                            argumentComponentContext,
                            featureFieldPath
                        )
                    }
                    .orElseThrow()
            }
            // Case 2: Domain data element source in which this data element field is canonical has
            // not been connected but there are variables matching the domain data element source's
            // required arguments
            connectorContext.materializationMetamodel
                .domainDataElementDataSourcePathByDescendentPath
                .invoke(shortestCanonicalPathForCoordinates)
                .exists { dp: GQLOperationPath ->
                    connectorContext.matchingArgumentPathsToVariableKeyByDomainDataElementPaths
                        .containsKey(dp) &&
                        connectorContext.materializationMetamodel
                            .domainSpecifiedDataElementSourceByPath
                            .getOrNone(dp)
                            .exists { dsdes: DomainSpecifiedDataElementSource ->
                                dsdes.domainArgumentsWithoutDefaultValuesByPath.keys.all {
                                    ap: GQLOperationPath ->
                                    connectorContext
                                        .matchingArgumentPathsToVariableKeyByDomainDataElementPaths
                                        .getOrElse(dp, ::persistentMapOf)
                                        .containsKey(ap)
                                }
                            }
                } -> {
                connectorContext.materializationMetamodel
                    .domainDataElementDataSourcePathByDescendentPath
                    .invoke(shortestCanonicalPathForCoordinates)
                    .flatMap { dp: GQLOperationPath ->
                        connectorContext.matchingArgumentPathsToVariableKeyByDomainDataElementPaths
                            .getOrNone(dp)
                    }
                    .successIfDefined {
                        ServiceError.of(
                            "matching argument paths to variable key for domain data element path for [ %s ]",
                            shortestCanonicalPathForCoordinates
                        )
                    }
                    .flatMap { avars: Map<GQLOperationPath, String> ->
                        connectorContext.gqlDocumentComposer.composeDocumentFromSpecWithSchema(
                            connectorContext.gqlDocumentSpecFactory
                                .builder()
                                .addFieldPath(shortestCanonicalPathForCoordinates)
                                .putAllArgumentPathsForVariableNames(
                                    avars.asSequence().map { (ap, vk) -> vk to ap }.asIterable()
                                )
                                .build(),
                            connectorContext.materializationMetamodel.materializationGraphQLSchema
                        )
                    }
                    .flatMap { d: Document ->
                        LazyStandardQueryTraverser.invoke(connectorContext.update { document(d) })
                            .asSequence()
                            .foldIntoTry(connectorContext) {
                                sq: StandardQuery,
                                qcc: QueryComponentContext ->
                                if (qcc.path in sq.requestGraph) {
                                    sq
                                } else {
                                    when (qcc) {
                                        is ArgumentComponentContext -> {
                                            connectArgument(sq, qcc)
                                        }
                                        is FieldComponentContext -> {
                                            connectField(sq, qcc)
                                        }
                                    }
                                }
                            }
                    }
                    .map { sq: StandardQuery ->
                        sq.update {
                            requestGraph(
                                    sq.requestGraph
                                        .put(
                                            argumentComponentContext.path,
                                            argumentComponentContext
                                        )
                                        .putEdge(
                                            featureFieldPath,
                                            argumentComponentContext.path,
                                            MaterializationEdge.EXTRACT_FROM_SOURCE
                                        )
                                        .putEdge(
                                            argumentComponentContext.path,
                                            shortestCanonicalPathForCoordinates,
                                            MaterializationEdge.EXTRACT_FROM_SOURCE
                                        )
                                )
                                .putConnectedPathForCanonicalPath(
                                    argumentComponentContext.canonicalPath,
                                    argumentComponentContext.path
                                )
                        }
                    }
                    .orElseThrow()
            }
            // Case 3: Domain data element source in which this data element field is canonical has
            // not been connected but there is a raw input context key for this source
            connectorContext.materializationMetamodel
                .domainDataElementDataSourcePathByDescendentPath
                .invoke(shortestCanonicalPathForCoordinates)
                .exists { dp: GQLOperationPath ->
                    connectorContext.materializationMetamodel.domainSpecifiedDataElementSourceByPath
                        .getOrNone(dp)
                        .exists { dsdes: DomainSpecifiedDataElementSource ->
                            dsdes.domainFieldCoordinates.fieldName in
                                connectorContext.rawInputContextKeys
                        }
                } -> {
                connectorContext.materializationMetamodel
                    .domainDataElementDataSourcePathByDescendentPath
                    .invoke(shortestCanonicalPathForCoordinates)
                    .flatMap { dp: GQLOperationPath ->
                        connectorContext.materializationMetamodel
                            .domainSpecifiedDataElementSourceByPath
                            .getOrNone(dp)
                    }
                    .successIfDefined {
                        ServiceError.of(
                            "domain data element source not found at matched path [ %s ] for descendent [ %s ]",
                            connectorContext.materializationMetamodel
                                .domainDataElementDataSourcePathByDescendentPath
                                .invoke(shortestCanonicalPathForCoordinates)
                                .orNull(),
                            shortestCanonicalPathForCoordinates
                        )
                    }
                    .flatMap { dsdes: DomainSpecifiedDataElementSource ->
                        connectorContext.gqlDocumentComposer.composeDocumentFromSpecWithSchema(
                            connectorContext.gqlDocumentSpecFactory
                                .builder()
                                .addFieldPath(shortestCanonicalPathForCoordinates)
                                .putAllArgumentPathsForVariableNames(
                                    dsdes.allArgumentsByPath
                                        .asSequence()
                                        .map { (ap: GQLOperationPath, ga: GraphQLArgument) ->
                                            buildString {
                                                append(
                                                    RawInputContext
                                                        .RAW_INPUT_CONTEXT_VARIABLE_PREFIX
                                                )
                                                append('_')
                                                append(dsdes.domainFieldCoordinates.fieldName)
                                                append('_')
                                                append(ga.name)
                                            } to ap
                                        }
                                        .asIterable()
                                )
                                .build(),
                            connectorContext.materializationMetamodel.materializationGraphQLSchema
                        )
                    }
                    .flatMap { d: Document ->
                        LazyStandardQueryTraverser.invoke(connectorContext.update { document(d) })
                            .asSequence()
                            .foldIntoTry(connectorContext) {
                                sq: StandardQuery,
                                qcc: QueryComponentContext ->
                                if (qcc.path in sq.requestGraph) {
                                    sq
                                } else {
                                    when (qcc) {
                                        is ArgumentComponentContext -> {
                                            connectArgument(sq, qcc)
                                        }
                                        is FieldComponentContext -> {
                                            connectField(sq, qcc)
                                        }
                                    }
                                }
                            }
                    }
                    .map { sq: StandardQuery ->
                        sq.update {
                            requestGraph(
                                    sq.requestGraph
                                        .put(
                                            argumentComponentContext.path,
                                            argumentComponentContext
                                        )
                                        .putEdge(
                                            featureFieldPath,
                                            argumentComponentContext.path,
                                            MaterializationEdge.EXTRACT_FROM_SOURCE
                                        )
                                        .putEdge(
                                            argumentComponentContext.path,
                                            shortestCanonicalPathForCoordinates,
                                            MaterializationEdge.EXTRACT_FROM_SOURCE
                                        )
                                )
                                .putConnectedPathForCanonicalPath(
                                    argumentComponentContext.canonicalPath,
                                    argumentComponentContext.path
                                )
                        }
                    }
                    .orElseThrow()
            }
            else -> {
                connectedDomainDataElementPaths
                    .asSequence()
                    .foldIntoTry(connectorContext) {
                        sq: StandardQuery,
                        connectedDomainDataElementPath: GQLOperationPath ->
                        connectFeatureArgumentToSpecificDataElementFieldWithinConnectedDomainDataElementSource(
                            sq,
                            connectedDomainDataElementPath,
                            dataElementCoordinates,
                            argumentComponentContext,
                            featureFieldPath
                        )
                    }
                    .orElseThrow()
            }
        }
    }

    private fun getFirstCoordinatesToUnconnectedRawInputDataElementSourcePathPairsOfUnconnectedDataElementFieldMatchingFeatureArgument(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext
    ): Option<Pair<FieldCoordinates, Set<GQLOperationPath>>> {
        return getDataElementFieldCoordinatesWithAliasMatchingArgumentName(
                connectorContext,
                argumentComponentContext
            )
            .orElse {
                connectorContext.materializationMetamodel.dataElementFieldCoordinatesByFieldName
                    .getOrNone(argumentComponentContext.argument.name)
                    .filter(ImmutableSet<FieldCoordinates>::isNotEmpty)
            }
            .fold(::emptySequence, ImmutableSet<FieldCoordinates>::asSequence)
            .flatMap { fc: FieldCoordinates ->
                connectorContext.materializationMetamodel.pathsByFieldCoordinates
                    .getOrElse(fc, ::persistentSetOf)
                    .asSequence()
                    .filter { p: GQLOperationPath ->
                        !connectorContext.connectedPathsByCanonicalPath.containsKey(p)
                    }
                    .flatMap { p: GQLOperationPath ->
                        connectorContext.materializationMetamodel
                            .domainDataElementDataSourcePathByDescendentPath
                            .invoke(p)
                            .sequence()
                    }
                    .filter { dp: GQLOperationPath ->
                        connectorContext.materializationMetamodel
                            .domainSpecifiedDataElementSourceByPath
                            .getOrNone(dp)
                            .exists { dsdes: DomainSpecifiedDataElementSource ->
                                dsdes.domainFieldCoordinates.fieldName in
                                    connectorContext.rawInputContextKeys &&
                                    !connectorContext.connectedPathsByCanonicalPath.containsKey(dp)
                            }
                    }
                    .toSet()
                    .let { dps: Set<GQLOperationPath> ->
                        if (dps.isNotEmpty()) {
                            sequenceOf(fc to dps)
                        } else {
                            emptySequence()
                        }
                    }
            }
            .firstOrNone()
    }

    private fun connectFeatureFieldArgumentToDataElementFieldOnUnconnectedRawInputDataElementSource(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext
    ): StandardQuery {
        val (dataElementCoordinates: FieldCoordinates, dsdesps: Set<GQLOperationPath>) =
            getFirstCoordinatesToUnconnectedRawInputDataElementSourcePathPairsOfUnconnectedDataElementFieldMatchingFeatureArgument(
                    connectorContext,
                    argumentComponentContext
                )
                .successIfDefined {
                    ServiceError.of(
                        """first coordinates to unconnected raw input data element source paths pair 
                        |expected but not found for matching argument [ path: %s ]"""
                            .flatten(),
                        argumentComponentContext.path
                    )
                }
                .orElseThrow()
        val shortestCanonicalPathForCoordinates: GQLOperationPath =
            connectorContext.materializationMetamodel.pathsByFieldCoordinates
                .getOrElse(dataElementCoordinates, ::persistentSetOf)
                .minOrNull()
                .toOption()
                .successIfDefined {
                    ServiceError.of(
                        "shortest path not found for [ data_element_field_coordinates: %s ] when attempting to connect [ feature.argument.path: %s ]",
                        dataElementCoordinates,
                        argumentComponentContext.path
                    )
                }
                .orElseThrow()
        val featureFieldPath: GQLOperationPath =
            argumentComponentContext.path
                .getParentPath()
                .filter { p: GQLOperationPath -> connectorContext.requestGraph.contains(p) }
                .successIfDefined {
                    ServiceError.of(
                        "feature_argument [ argument.name: %s ] to feature field path [ %s ] not found in request_materialization_graph",
                        argumentComponentContext.argument.name,
                        argumentComponentContext.path.getParentPath().orNull()
                    )
                }
                .orElseThrow()
        return connectorContext.materializationMetamodel
            .domainDataElementDataSourcePathByDescendentPath
            .invoke(shortestCanonicalPathForCoordinates)
            .flatMap { dp: GQLOperationPath ->
                connectorContext.materializationMetamodel.domainSpecifiedDataElementSourceByPath
                    .getOrNone(dp)
            }
            .successIfDefined {
                ServiceError.of(
                    "domain data element source not found at matched path [ %s ] for descendent [ %s ]",
                    connectorContext.materializationMetamodel
                        .domainDataElementDataSourcePathByDescendentPath
                        .invoke(shortestCanonicalPathForCoordinates)
                        .orNull(),
                    shortestCanonicalPathForCoordinates
                )
            }
            .map { dsdes: DomainSpecifiedDataElementSource ->
                if (dsdesps.contains(dsdes.domainPath)) {
                    dsdes
                } else {
                    throw ServiceError.of(
                        "the domain_data_element_source for which [ coordinates: %s ] has the shortest path is not one of the raw_input_context sources %s",
                        dataElementCoordinates,
                        dsdesps.joinToString(", ", "{ ", " }")
                    )
                }
            }
            .flatMap { dsdes: DomainSpecifiedDataElementSource ->
                connectorContext.gqlDocumentComposer.composeDocumentFromSpecWithSchema(
                    connectorContext.gqlDocumentSpecFactory
                        .builder()
                        .addFieldPath(shortestCanonicalPathForCoordinates)
                        .putAllArgumentPathsForVariableNames(
                            dsdes.allArgumentsByPath
                                .asSequence()
                                .map { (ap: GQLOperationPath, ga: GraphQLArgument) ->
                                    buildString {
                                        append(RawInputContext.RAW_INPUT_CONTEXT_VARIABLE_PREFIX)
                                        append('_')
                                        append(dsdes.domainFieldCoordinates.fieldName)
                                        append('_')
                                        append(ga.name)
                                    } to ap
                                }
                                .asIterable()
                        )
                        .build(),
                    connectorContext.materializationMetamodel.materializationGraphQLSchema
                )
            }
            .flatMap { d: Document ->
                LazyStandardQueryTraverser.invoke(connectorContext.update { document(d) })
                    .asSequence()
                    .foldIntoTry(connectorContext) { sq: StandardQuery, qcc: QueryComponentContext
                        ->
                        if (qcc.path in sq.requestGraph) {
                            sq
                        } else {
                            when (qcc) {
                                is ArgumentComponentContext -> {
                                    connectArgument(sq, qcc)
                                }
                                is FieldComponentContext -> {
                                    connectField(sq, qcc)
                                }
                            }
                        }
                    }
            }
            .map { sq: StandardQuery ->
                sq.update {
                    requestGraph(
                            sq.requestGraph
                                .put(argumentComponentContext.path, argumentComponentContext)
                                .putEdge(
                                    featureFieldPath,
                                    argumentComponentContext.path,
                                    MaterializationEdge.EXTRACT_FROM_SOURCE
                                )
                                .putEdge(
                                    argumentComponentContext.path,
                                    shortestCanonicalPathForCoordinates,
                                    MaterializationEdge.EXTRACT_FROM_SOURCE
                                )
                        )
                        .putConnectedPathForCanonicalPath(
                            argumentComponentContext.canonicalPath,
                            argumentComponentContext.path
                        )
                }
            }
            .orElseThrow()
    }

    private fun getFirstCoordinatesToDataElementFieldsOnUnconnectedDataElementSourceWithCompleteVariableInputSetMatchingFeatureArgument(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext,
    ): Option<Triple<GQLOperationPath, FieldCoordinates, GQLOperationPath>> {
        return when {
            connectorContext.variableKeys.isEmpty() -> {
                none()
            }
            else -> {
                getDataElementFieldCoordinatesWithAliasMatchingArgumentName(
                        connectorContext,
                        argumentComponentContext
                    )
                    .orElse {
                        connectorContext.materializationMetamodel
                            .dataElementFieldCoordinatesByFieldName
                            .getOrNone(argumentComponentContext.argument.name)
                            .filter(ImmutableSet<FieldCoordinates>::isNotEmpty)
                    }
                    .map(ImmutableSet<FieldCoordinates>::asSequence)
                    .getOrElse(::emptySequence)
                    .flatMap { fc: FieldCoordinates ->
                        connectorContext.materializationMetamodel.pathsByFieldCoordinates
                            .getOrElse(fc, ::persistentSetOf)
                            .asSequence()
                            .filter { p: GQLOperationPath ->
                                connectorContext.materializationMetamodel.dataElementElementTypePath
                                    .isAncestorTo(p) &&
                                    p !in connectorContext.connectedPathsByCanonicalPath
                            }
                            .flatMap { p: GQLOperationPath ->
                                connectorContext.materializationMetamodel
                                    .domainDataElementDataSourcePathByDescendentPath
                                    .invoke(p)
                                    .filter { dp: GQLOperationPath ->
                                        !connectorContext.connectedPathsByCanonicalPath.containsKey(
                                            dp
                                        ) &&
                                            connectorContext
                                                .matchingArgumentPathsToVariableKeyByDomainDataElementPaths
                                                .containsKey(dp) &&
                                            connectorContext.materializationMetamodel
                                                .domainSpecifiedDataElementSourceByPath
                                                .getOrNone(dp)
                                                .exists { dsdes: DomainSpecifiedDataElementSource ->
                                                    connectorContext
                                                        .matchingArgumentPathsToVariableKeyByDomainDataElementPaths
                                                        .getOrElse(dp, ::persistentMapOf)
                                                        .keys
                                                        .containsAll(
                                                            dsdes
                                                                .domainArgumentsWithoutDefaultValuesByPath
                                                                .keys
                                                        )
                                                }
                                    }
                                    .map { dp: GQLOperationPath -> dp to p }
                                    .sequence()
                            }
                            .minByOrNull { (_: GQLOperationPath, p: GQLOperationPath) -> p }
                            .toOption()
                            .map { (dp: GQLOperationPath, p: GQLOperationPath) ->
                                Triple(dp, fc, p)
                            }
                            .sequence()
                    }
                    .firstOrNone()
            }
        }
    }

    private fun getDataElementFieldCoordinatesWithAliasMatchingArgumentName(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext,
    ): Option<ImmutableSet<FieldCoordinates>> {
        return connectorContext.materializationMetamodel.aliasCoordinatesRegistry
            .getFieldsWithAlias(argumentComponentContext.argument.name)
            .toOption()
            .filter(ImmutableSet<FieldCoordinates>::isNotEmpty)
            .orElse {
                connectorContext.materializationMetamodel.aliasCoordinatesRegistry
                    .getFieldsWithAlias(argumentComponentContext.argument.name.toCamelCase())
                    .toOption()
                    .filter(ImmutableSet<FieldCoordinates>::isNotEmpty)
            }
            .map { fcs: ImmutableSet<FieldCoordinates> ->
                // TODO: Memoize this operation
                // logger.debug(
                //    "fields_with_alias: [ argument.name: {}, fcs: {} ]",
                //    argumentComponentContext.argument.name,
                //    fcs
                // )
                fcs.asSequence()
                    .filter { fc: FieldCoordinates ->
                        connectorContext.materializationMetamodel.fieldCoordinatesAvailableUnderPath
                            .invoke(
                                fc,
                                connectorContext.materializationMetamodel.dataElementElementTypePath
                            )
                    }
                    .toPersistentSet()
                // .also { fcsUnderPath ->
                //    logger.debug(
                //        "fields_with_alias_under_data_element_type_path: [ argument.name: {},
                // fcs_under_de: {} ]",
                //        argumentComponentContext.argument.name,
                //        fcsUnderPath
                //    )
                // }
            }
            .filter(ImmutableSet<FieldCoordinates>::isNotEmpty)
    }

    private fun connectFeatureFieldArgumentToDataElementFieldsOnUnconnectedDataElementSourceWithCompleteVariableInputSet(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext,
    ): StandardQuery {
        val (
            domainPath: GQLOperationPath,
            dataElementCoordinates: FieldCoordinates,
            dataElementFieldPath: GQLOperationPath) =
            getFirstCoordinatesToDataElementFieldsOnUnconnectedDataElementSourceWithCompleteVariableInputSetMatchingFeatureArgument(
                    connectorContext,
                    argumentComponentContext
                )
                .successIfDefined {
                    ServiceError.of(
                        """argument [ path: %s ] does not match expected 
                            |unconnected data element source with 
                            |complete variable input set case"""
                            .flatten(),
                        argumentComponentContext.path
                    )
                }
                .orElseThrow()
        logger.debug(
            """connect_feature_field_argument_to_
            |unconnected_data_element_on_
            |unconnected_data_element_source_
            |with_complete_variable_input_set: 
            |[ domain_path: {},  
            |data_element_field_coordinates: {}, 
            |data_element_field_path: {} ]"""
                .flatten(),
            domainPath,
            dataElementCoordinates,
            dataElementFieldPath
        )
        val featureFieldPath: GQLOperationPath =
            argumentComponentContext.path
                .getParentPath()
                .filter { p: GQLOperationPath -> connectorContext.requestGraph.contains(p) }
                .successIfDefined {
                    ServiceError.of(
                        "feature_field of feature_argument [ path: %s ] expected but not found in request_graph",
                        argumentComponentContext.path
                    )
                }
                .orElseThrow()
        val spec: GQLDocumentSpec =
            connectorContext.matchingArgumentPathsToVariableKeyByDomainDataElementPaths
                .getOrElse(domainPath, ::persistentMapOf)
                .entries
                .asSequence()
                .fold(
                    connectorContext.gqlDocumentSpecFactory
                        .builder()
                        .addFieldPath(dataElementFieldPath)
                ) { sb: GQLDocumentSpec.Builder, (p: GQLOperationPath, vk: String) ->
                    sb.putArgumentPathForVariableName(vk, p)
                }
                .build()
        return connectorContext.gqlDocumentComposer
            .composeDocumentFromSpecWithSchema(
                spec,
                connectorContext.materializationMetamodel.materializationGraphQLSchema
            )
            .flatMap { d: Document ->
                // Note: If StandardQuery is made mutable, this creation of new instance
                // with a different document will cause issues later
                // Immutability is great for keeping this same big object with only one
                // small difference
                LazyStandardQueryTraverser.invoke(connectorContext.update { document(d) })
                    .foldIntoTry(connectorContext) { sq: StandardQuery, qcc: QueryComponentContext
                        ->
                        if (qcc.path in sq.requestGraph) {
                            sq
                        } else {
                            when (qcc) {
                                is ArgumentComponentContext -> {
                                    connectArgument(sq, qcc)
                                }
                                is FieldComponentContext -> {
                                    connectField(sq, qcc)
                                }
                            }
                        }
                    }
            }
            .map { sq: StandardQuery ->
                sq.update {
                    requestGraph(
                        sq.requestGraph
                            .put(argumentComponentContext.path, argumentComponentContext)
                            .putEdge(
                                featureFieldPath,
                                argumentComponentContext.path,
                                MaterializationEdge.EXTRACT_FROM_SOURCE
                            )
                            .putEdge(
                                argumentComponentContext.path,
                                dataElementFieldPath,
                                MaterializationEdge.EXTRACT_FROM_SOURCE
                            )
                    )
                    putConnectedPathForCanonicalPath(
                        argumentComponentContext.canonicalPath,
                        argumentComponentContext.path
                    )
                }
            }
            .orElseThrow()
    }

    private fun connectFeatureArgumentToDirectArgumentValue(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext
    ): StandardQuery {
        val fieldPath: GQLOperationPath =
            argumentComponentContext.path
                .getParentPath()
                .filter { p: GQLOperationPath -> connectorContext.requestGraph.contains(p) }
                .successIfDefined {
                    ServiceError.of(
                        "feature_argument [ %s ] parent field path [ %s ] not found in request_materialization_graph",
                        argumentComponentContext.path,
                        argumentComponentContext.path.getParentPath().orNull()
                    )
                }
                .orElseThrow()
        val argumentValue: Value<*> =
            getNonDefaultArgumentValueForFeatureArgument(connectorContext, argumentComponentContext)
                .successIfDefined {
                    ServiceError.of(
                        "direct argument value expected but not found for feature argument [ path: %s ]",
                        argumentComponentContext.path
                    )
                }
                .orElseThrow()
        return connectorContext.update {
            requestGraph(
                connectorContext.requestGraph
                    .put(argumentComponentContext.path, argumentComponentContext)
                    .putEdge(
                        fieldPath,
                        argumentComponentContext.path,
                        MaterializationEdge.DIRECT_ARGUMENT_VALUE_PROVIDED
                    )
            )
            putConnectedPathForCanonicalPath(
                argumentComponentContext.canonicalPath,
                argumentComponentContext.path
            )
        }
    }

    private fun getNonDefaultArgumentValueForFeatureArgument(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext
    ): Option<Value<*>> {
        return connectorContext.materializationMetamodel
            .featureSpecifiedFeatureCalculatorsByCoordinates
            .getOrNone(argumentComponentContext.fieldCoordinates)
            .flatMap { fsfc: FeatureSpecifiedFeatureCalculator ->
                fsfc.argumentsByName.getOrNone(argumentComponentContext.argument.name)
            }
            .flatMap { ga: GraphQLArgument ->
                // Case 1: Arg default value not set; this arg value is non-null
                ga.argumentDefaultValue
                    .toOption()
                    .filter(InputValueWithState::isNotSet)
                    .and(
                        argumentComponentContext.argument.toOption().mapNotNull(Argument::getValue)
                    )
                    .orElse {
                        // Case 2: Arg default value is set but this arg value not equal to set
                        // default value
                        ga.argumentDefaultValue
                            .toOption()
                            .filter(InputValueWithState::isLiteral)
                            .filter(InputValueWithState::isSet)
                            .mapNotNull(InputValueWithState::getValue)
                            .filterIsInstance<Value<*>>()
                            .flatMap { dv: Value<*> ->
                                argumentComponentContext.argument.value.toOption().filter {
                                    v: Value<*> ->
                                    dv != v
                                }
                            }
                    }
                    .orElse {
                        // Case 3: Arg has list type; this arg value is non-empty list
                        ga.type
                            .toOption()
                            .filter(GraphQLTypeUtil::isList)
                            .and(
                                argumentComponentContext.argument.value
                                    .toOption()
                                    .filterIsInstance<ArrayValue>()
                                    .map(ArrayValue::getValues)
                                    .filter(List<Value<*>>::isNotEmpty)
                            )
                            .and(argumentComponentContext.argument.value.toOption())
                    }
                    .orElse {
                        // Case 4: Arg has non-null list type; this arg value is non-empty list
                        ga.type
                            .toOption()
                            .filter(GraphQLTypeUtil::isNonNull)
                            .map(GraphQLTypeUtil::unwrapNonNull)
                            .filter(GraphQLTypeUtil::isList)
                            .and(
                                argumentComponentContext.argument.value
                                    .toOption()
                                    .filterIsInstance<ArrayValue>()
                                    .map(ArrayValue::getValues)
                                    .filter(List<Value<*>>::isNotEmpty)
                            )
                            .and(argumentComponentContext.argument.value.toOption())
                    }
            }
    }

    private fun connectTransformerArgument(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext
    ): StandardQuery {
        logger.debug("connect_transformer_argument: [ path: {} ]", argumentComponentContext.path)
        return connectTransformerFieldToTransformerArgument(
            connectorContext,
            argumentComponentContext
        )
    }

    private fun connectTransformerFieldToTransformerArgument(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext,
    ): StandardQuery {
        val fp: GQLOperationPath =
            argumentComponentContext.path
                .getParentPath()
                .filter { p: GQLOperationPath ->
                    connectorContext.requestGraph
                        .get(p)
                        .toOption()
                        .filterIsInstance<FieldComponentContext>()
                        .filter { sfcc: FieldComponentContext ->
                            sfcc.fieldCoordinates == argumentComponentContext.fieldCoordinates
                        }
                        .isDefined()
                }
                .successIfDefined {
                    ServiceError.of(
                        """transformer_field [ path: %s ] corresponding 
                            |to transformer_argument [ path: %s ] 
                            |not found in request_graph"""
                            .flatten(),
                        argumentComponentContext.path.getParentPath().orNull(),
                        argumentComponentContext.path
                    )
                }
                .orElseThrow()
        val e: MaterializationEdge =
            when {
                argumentComponentContext.argument.value
                    .toOption()
                    .filterIsInstance<VariableReference>()
                    .filter { vr: VariableReference -> vr.name in connectorContext.variableKeys }
                    .isDefined() -> {
                    MaterializationEdge.VARIABLE_VALUE_PROVIDED
                }
                argumentComponentContext.argument.value
                    .toOption()
                    .filter { v: Value<*> -> v !is NullValue }
                    .isDefined() -> {
                    MaterializationEdge.DIRECT_ARGUMENT_VALUE_PROVIDED
                }
                connectorContext.materializationMetamodel
                    .transformerSpecifiedTransformerSourcesByCoordinates
                    .getOrNone(argumentComponentContext.fieldCoordinates)
                    .map(TransformerSpecifiedTransformerSource::argumentsByName)
                    .flatMap { aByName: ImmutableMap<String, GraphQLArgument> ->
                        aByName.getOrNone(argumentComponentContext.argument.name)
                    }
                    .filter { ga: GraphQLArgument -> ga.hasSetDefaultValue() }
                    .isDefined() -> {
                    MaterializationEdge.DEFAULT_ARGUMENT_VALUE_PROVIDED
                }
                else -> {
                    throw ServiceError.of(
                        """unable to connect transformer field [ path: %s ] 
                            |to its argument [ path: %s ]; 
                            |variable, default argument, or direct argument value 
                            |is missing"""
                            .flatten(),
                        argumentComponentContext.path.getParentPath().orNull(),
                        argumentComponentContext.path
                    )
                }
            }
        return connectorContext.update {
            requestGraph(
                connectorContext.requestGraph
                    .put(argumentComponentContext.path, argumentComponentContext)
                    .putEdge(fp, argumentComponentContext.path, e)
            )
        }
    }

    override fun connectField(
        connectorContext: StandardQuery,
        fieldComponentContext: FieldComponentContext
    ): StandardQuery {
        logger.debug(
            "connect_field: [ field_component_context { path: {}, coordinates: {}, canonical_path: {} } ]",
            fieldComponentContext.path,
            fieldComponentContext.fieldCoordinates,
            fieldComponentContext.canonicalPath
        )
        return when {
            fieldComponentContext.fieldCoordinates in
                connectorContext.materializationMetamodel.elementTypeCoordinates -> {
                connectElementTypeField(connectorContext, fieldComponentContext)
            }
            connectorContext.materializationMetamodel.dataElementElementTypePath.isAncestorTo(
                fieldComponentContext.canonicalPath
            ) -> {
                connectDataElementField(connectorContext, fieldComponentContext)
            }
            connectorContext.materializationMetamodel.featureElementTypePath.isAncestorTo(
                fieldComponentContext.canonicalPath
            ) -> {
                connectFeatureField(connectorContext, fieldComponentContext)
            }
            connectorContext.materializationMetamodel.transformerElementTypePath.isAncestorTo(
                fieldComponentContext.canonicalPath
            ) -> {
                connectTransformerField(connectorContext, fieldComponentContext)
            }
            else -> {
                throw ServiceError.of(
                    "unable to identify element_type bucket for selected_field [ path: %s ]",
                    fieldComponentContext.path
                )
            }
        }
    }

    private fun connectElementTypeField(
        connectorContext: StandardQuery,
        fieldComponentContext: FieldComponentContext,
    ): StandardQuery {
        logger.debug(
            "connect_element_type_field: [ field_component_context.path: {} ]",
            fieldComponentContext.path
        )
        return when {
            // Case 1: Element type field has already been connected
            fieldComponentContext.path in connectorContext.requestGraph -> {
                connectorContext
            }
            // Case 2: Element type field points to the DataElement field on Query
            fieldComponentContext.fieldCoordinates ==
                connectorContext.materializationMetamodel.featureEngineeringModel
                    .dataElementFieldCoordinates -> {
                connectorContext.update {
                    requestGraph(
                        connectorContext.requestGraph.put(
                            fieldComponentContext.path,
                            fieldComponentContext
                        )
                    )
                    putConnectedFieldPathForCoordinates(
                        fieldComponentContext.fieldCoordinates,
                        fieldComponentContext.path
                    )
                    putConnectedPathForCanonicalPath(
                        fieldComponentContext.canonicalPath,
                        fieldComponentContext.path
                    )
                }
            }
            else -> {
                // Case 3: Element type field points to Feature or Transformer field on Query
                connectorContext.update {
                    putConnectedFieldPathForCoordinates(
                        fieldComponentContext.fieldCoordinates,
                        fieldComponentContext.path
                    )
                    putConnectedPathForCanonicalPath(
                        fieldComponentContext.canonicalPath,
                        fieldComponentContext.path
                    )
                }
            }
        }
    }

    private fun connectDataElementField(
        connectorContext: StandardQuery,
        fieldComponentContext: FieldComponentContext,
    ): StandardQuery {
        logger.debug(
            "connect_data_element_field: [ field_component_context.path: {} ]",
            fieldComponentContext.path
        )
        return when {
            fieldComponentContext.fieldCoordinates in
                connectorContext.materializationMetamodel
                    .domainSpecifiedDataElementSourceByCoordinates -> {
                connectDomainDataElementField(connectorContext, fieldComponentContext)
            }
            else -> {
                connectSubdomainDataElementField(connectorContext, fieldComponentContext)
            }
        }
    }

    private fun connectDomainDataElementField(
        connectorContext: StandardQuery,
        fieldComponentContext: FieldComponentContext
    ): StandardQuery {
        val decb: DataElementCallable.Builder =
            connectorContext.materializationMetamodel.domainSpecifiedDataElementSourceByCoordinates
                .getOrNone(fieldComponentContext.fieldCoordinates)
                .successIfDefined {
                    ServiceError.of(
                        "%s not found at [ coordinates: %s ]",
                        DomainSpecifiedDataElementSource::class.simpleName,
                        fieldComponentContext.fieldCoordinates
                    )
                }
                .map { dsdes: DomainSpecifiedDataElementSource ->
                    dsdes.dataElementSource.builder().selectDomain(dsdes)
                }
                .orElseThrow()
        return connectorContext.update {
            requestGraph(
                connectorContext.requestGraph.put(fieldComponentContext.path, fieldComponentContext)
            )
            putDataElementCallableBuilderForPath(fieldComponentContext.path, decb)
            putConnectedFieldPathForCoordinates(
                fieldComponentContext.fieldCoordinates,
                fieldComponentContext.path
            )
            putConnectedPathForCanonicalPath(
                fieldComponentContext.canonicalPath,
                fieldComponentContext.path
            )
        }
    }

    private fun connectSubdomainDataElementField(
        connectorContext: StandardQuery,
        fieldComponentContext: FieldComponentContext,
    ): StandardQuery {
        logger.debug(
            "connect_subdomain_data_element_field: [ field_component_context.path: {} ]",
            fieldComponentContext.path
        )
        return Try.success(connectorContext)
            .map { sq: StandardQuery ->
                connectLastUpdatedDataElementFieldRelatedToSubdomainDataElementField(
                    sq,
                    fieldComponentContext
                )
            }
            .map { sq: StandardQuery ->
                connectDataElementFieldToDomainDataElementField(sq, fieldComponentContext)
            }
            .orElseThrow()
    }

    private fun connectLastUpdatedDataElementFieldRelatedToSubdomainDataElementField(
        connectorContext: StandardQuery,
        fieldComponentContext: FieldComponentContext
    ): StandardQuery {
        return fieldComponentContext.path
            .getParentPath()
            .flatMap { selectedFieldParentPath: GQLOperationPath ->
                if (selectedFieldParentPath in connectorContext.dataElementCallableBuildersByPath) {
                    connectorContext.requestGraph
                        .get(selectedFieldParentPath)
                        .toOption()
                        .filterIsInstance<FieldComponentContext>()
                } else {
                    connectorContext.requestGraph
                        .successorVertices(selectedFieldParentPath)
                        .firstOrNone()
                        .map { (_: GQLOperationPath, qcc: QueryComponentContext) -> qcc }
                        .filterIsInstance<FieldComponentContext>()
                }
            }
            .filter { lastUpdatedFieldDomainContext: FieldComponentContext ->
                lastUpdatedFieldDomainContext.fieldCoordinates in
                    connectorContext.materializationMetamodel
                        .domainSpecifiedDataElementSourceByCoordinates
            }
            .flatMap { lastUpdatedFieldDomainContext: FieldComponentContext ->
                connectorContext.materializationMetamodel
                    .domainSpecifiedDataElementSourceByCoordinates
                    .getOrNone(lastUpdatedFieldDomainContext.fieldCoordinates)
                    .flatMap { dsdes: DomainSpecifiedDataElementSource ->
                        dsdes.lastUpdatedCoordinatesRegistry
                            .findNearestLastUpdatedField(fieldComponentContext.canonicalPath)
                            .filter { (_: GQLOperationPath, fcs: Set<FieldCoordinates>) ->
                                fieldComponentContext.fieldCoordinates !in fcs
                            }
                            .map { (p: GQLOperationPath, fcs: Set<FieldCoordinates>) ->
                                Triple(
                                    dsdes.domainPath.transform {
                                        appendSelections(
                                            p.selection.drop(dsdes.domainPath.selection.size)
                                        )
                                    },
                                    p,
                                    fcs
                                )
                            }
                    }
                    .flatMap {
                        (p: GQLOperationPath, cp: GQLOperationPath, fcs: Set<FieldCoordinates>) ->
                        fcs.asSequence()
                            .firstOrNone { fc: FieldCoordinates ->
                                lastUpdatedFieldDomainContext.fieldCoordinates.typeName ==
                                    fc.typeName
                            }
                            .orElse { fcs.firstOrNone() }
                            .map { fc: FieldCoordinates ->
                                connectorContext.queryComponentContextFactory
                                    .fieldComponentContextBuilder()
                                    .field(Field.newField().name(fc.fieldName).build())
                                    .fieldCoordinates(fc)
                                    .path(p)
                                    .canonicalPath(cp)
                                    .build()
                            }
                    }
            }
            .map { lastUpdatedFieldContext: FieldComponentContext ->
                when {
                    !connectorContext.requestGraph.contains(lastUpdatedFieldContext.path) -> {
                        connectField(
                            connectorContext.update {
                                putLastUpdatedDataElementPathForDataElementPath(
                                    fieldComponentContext.path,
                                    lastUpdatedFieldContext.path
                                )
                            },
                            lastUpdatedFieldContext
                        )
                    }
                    else -> {
                        connectorContext.update {
                            putLastUpdatedDataElementPathForDataElementPath(
                                fieldComponentContext.path,
                                lastUpdatedFieldContext.path
                            )
                        }
                    }
                }
            }
            .getOrElse { connectorContext }
    }

    private fun connectDataElementFieldToDomainDataElementField(
        connectorContext: StandardQuery,
        fieldComponentContext: FieldComponentContext,
    ): StandardQuery {
        val parentPath: GQLOperationPath =
            fieldComponentContext.path
                .getParentPath()
                .successIfDefined {
                    ServiceError.of(
                        "the data_element_field should be processed by an element_type method [ field.name: %s ]",
                        fieldComponentContext.field.name
                    )
                }
                .orElseThrow()
        if (
            !connectorContext.requestGraph.contains(parentPath) ||
                connectorContext.requestGraph.successorVertices(parentPath).none()
        ) {
            throw ServiceError.of(
                """parent of selected_data_element should have been added 
                    |and connected to its domain prior to its child 
                    |data_element fields: [ parent_path: %s ]"""
                    .flatten(),
                parentPath
            )
        }
        return if (parentPath in connectorContext.dataElementCallableBuildersByPath) {
            connectorContext.update {
                requestGraph(
                    connectorContext.requestGraph
                        .put(fieldComponentContext.path, fieldComponentContext)
                        .putEdge(
                            fieldComponentContext.path,
                            parentPath,
                            MaterializationEdge.EXTRACT_FROM_SOURCE
                        )
                )
                putDataElementCallableBuilderForPath(
                    parentPath,
                    connectorContext.dataElementCallableBuildersByPath
                        .get(parentPath)!!
                        .selectPathWithinDomain(fieldComponentContext.canonicalPath)
                )
                putConnectedFieldPathForCoordinates(
                    fieldComponentContext.fieldCoordinates,
                    fieldComponentContext.path
                )
                putConnectedPathForCanonicalPath(
                    fieldComponentContext.canonicalPath,
                    fieldComponentContext.path
                )
            }
        } else {
            val (domainPath: GQLOperationPath, _: QueryComponentContext) =
                connectorContext.requestGraph.successorVertices(parentPath).first()
            if (domainPath !in connectorContext.dataElementCallableBuildersByPath) {
                throw ServiceError.of(
                    "domain_data_element_callable has not been created for [ path: %s ]",
                    domainPath
                )
            }
            connectorContext.update {
                requestGraph(
                    connectorContext.requestGraph
                        .put(fieldComponentContext.path, fieldComponentContext)
                        .putEdge(
                            fieldComponentContext.path,
                            domainPath,
                            MaterializationEdge.EXTRACT_FROM_SOURCE
                        )
                )
                putDataElementCallableBuilderForPath(
                    domainPath,
                    connectorContext.dataElementCallableBuildersByPath[domainPath]!!
                        .selectPathWithinDomain(fieldComponentContext.canonicalPath)
                )
                putConnectedFieldPathForCoordinates(
                    fieldComponentContext.fieldCoordinates,
                    fieldComponentContext.path
                )
                putConnectedPathForCanonicalPath(
                    fieldComponentContext.canonicalPath,
                    fieldComponentContext.path
                )
            }
        }
    }

    private fun connectTransformerField(
        connectorContext: StandardQuery,
        fieldComponentContext: FieldComponentContext,
    ): StandardQuery {
        logger.debug(
            "connect_transformer_field: [ field_component_context.path: {} ]",
            fieldComponentContext.path
        )
        return when {
            // Case 1: Transformer field has already been connected
            fieldComponentContext.path in connectorContext.transformerCallablesByPath -> {
                connectorContext
            }
            // Case 2: Transformer field maps to coordinates for
            // transformer_specified_transformer_source
            // Assumption: Every transformer has only one set of coordinates to which it can be
            // mapped
            fieldComponentContext.fieldCoordinates in
                connectorContext.materializationMetamodel
                    .transformerSpecifiedTransformerSourcesByCoordinates -> {
                connectTransformerFieldToItsTransformerSpecifiedTransformerSource(
                    connectorContext,
                    fieldComponentContext
                )
            }
            // Case 3: Transformer field acts as a container or "folder" for other transformer
            // fields
            fieldHasGraphQLFieldsContainerType(connectorContext, fieldComponentContext) -> {
                connectorContext
            }
            else -> {
                throw ServiceError.of(
                    "unhandled transformer_field case [ path: %s ]",
                    fieldComponentContext.path
                )
            }
        }
    }

    private fun connectTransformerFieldToItsTransformerSpecifiedTransformerSource(
        connectorContext: StandardQuery,
        fieldComponentContext: FieldComponentContext
    ): StandardQuery {
        val tsts: TransformerSpecifiedTransformerSource =
            connectorContext.materializationMetamodel
                .transformerSpecifiedTransformerSourcesByCoordinates
                .getOrNone(fieldComponentContext.fieldCoordinates)
                .successIfDefined {
                    ServiceError.of(
                        "%s not found at [ coordinates: %s ]",
                        TransformerSpecifiedTransformerSource::class.simpleName,
                        fieldComponentContext.fieldCoordinates
                    )
                }
                .orElseThrow()
        return connectorContext.update {
            requestGraph(
                connectorContext.requestGraph.put(fieldComponentContext.path, fieldComponentContext)
            )
            putTransformerCallableForPath(
                fieldComponentContext.path,
                tsts.transformerSource.builder().selectTransformer(tsts).build()
            )
            putConnectedFieldPathForCoordinates(
                fieldComponentContext.fieldCoordinates,
                fieldComponentContext.path
            )
            putConnectedPathForCanonicalPath(
                fieldComponentContext.canonicalPath,
                fieldComponentContext.path
            )
        }
    }

    private fun connectFeatureField(
        connectorContext: StandardQuery,
        fieldComponentContext: FieldComponentContext,
    ): StandardQuery {
        logger.debug(
            "connect_feature_field: [ field_component_context.path: {} ]",
            fieldComponentContext.path
        )
        return when {
            // Case 1: Feature field has already been connected
            fieldComponentContext.path in connectorContext.featureCalculatorCallablesByPath -> {
                connectorContext
            }
            // Case 2: Feature field maps to coordinates for feature_specified_feature_calculators
            // Assumption: Every feature has only one set of coordinates to which it can be mapped
            fieldComponentContext.fieldCoordinates in
                connectorContext.materializationMetamodel
                    .featureSpecifiedFeatureCalculatorsByCoordinates -> {
                connectFeatureCalculatorForFeatureField(connectorContext, fieldComponentContext)
            }
            // Case 3: Feature field does not need to be "calculated" but rather, acts as a
            // container for other feature fields
            fieldHasGraphQLFieldsContainerType(connectorContext, fieldComponentContext) -> {
                // feature object type containers do not require wiring
                connectorContext.update {
                    putConnectedFieldPathForCoordinates(
                        fieldComponentContext.fieldCoordinates,
                        fieldComponentContext.path
                    )
                    putConnectedPathForCanonicalPath(
                        fieldComponentContext.canonicalPath,
                        fieldComponentContext.path
                    )
                }
            }
            else -> {
                throw ServiceError.of(
                    "unhandled feature_field [ path: %s ]",
                    fieldComponentContext.path
                )
            }
        }
    }

    private fun fieldHasGraphQLFieldsContainerType(
        connectorContext: StandardQuery,
        fieldComponentContext: FieldComponentContext,
    ): Boolean {
        return extractGraphQLFieldDefinitionForCoordinates(
                connectorContext,
                fieldComponentContext.fieldCoordinates
            )
            .mapNotNull(GraphQLFieldDefinition::getType)
            .mapNotNull(GraphQLTypeUtil::unwrapAll)
            .filterIsInstance<GraphQLFieldsContainer>()
            .isDefined()
    }

    private fun extractGraphQLFieldDefinitionForCoordinates(
        connectorContext: StandardQuery,
        fieldCoordinates: FieldCoordinates
    ): Option<GraphQLFieldDefinition> {
        return fieldCoordinates.typeName
            .toOption()
            .flatMap { tn: String ->
                connectorContext.materializationMetamodel.materializationGraphQLSchema
                    .getType(tn)
                    .toOption()
            }
            .filterIsInstance<GraphQLCompositeType>()
            .flatMap { gct: GraphQLCompositeType ->
                try {
                    Introspection.getFieldDef(
                            connectorContext.materializationMetamodel.materializationGraphQLSchema,
                            gct,
                            fieldCoordinates.fieldName
                        )
                        .toOption()
                } catch (t: Throwable) {
                    None
                }
            }
    }

    private fun connectFeatureCalculatorForFeatureField(
        connectorContext: StandardQuery,
        fieldComponentContext: FieldComponentContext,
    ): StandardQuery {
        val fsfc: FeatureSpecifiedFeatureCalculator =
            connectorContext.materializationMetamodel
                .featureSpecifiedFeatureCalculatorsByCoordinates
                .getOrNone(fieldComponentContext.fieldCoordinates)
                .successIfDefined {
                    ServiceError.of(
                        "%s not found at [ coordinates: %s ]",
                        FeatureSpecifiedFeatureCalculator::class.simpleName,
                        fieldComponentContext.fieldCoordinates
                    )
                }
                .orElseThrow()
        val fcb: FeatureCalculatorCallable.Builder =
            fsfc.featureCalculator.builder().selectFeature(fsfc)
        val tc: TransformerCallable =
            connectorContext.materializationMetamodel
                .transformerSpecifiedTransformerSourcesByCoordinates
                .getOrNone(fsfc.transformerFieldCoordinates)
                .successIfDefined {
                    ServiceError.of(
                        "%s not found at [ coordinates: %s ]",
                        TransformerSpecifiedTransformerSource::class.simpleName,
                        fsfc.transformerFieldCoordinates
                    )
                }
                .map { tsts: TransformerSpecifiedTransformerSource ->
                    tsts.transformerSource.builder().selectTransformer(tsts).build()
                }
                .orElseThrow()
        return connectorContext.update {
            requestGraph(
                connectorContext.requestGraph.put(fieldComponentContext.path, fieldComponentContext)
            )
            putFeatureCalculatorCallableForPath(
                fieldComponentContext.path,
                fcb.setTransformerCallable(tc).build()
            )
            putConnectedFieldPathForCoordinates(
                fieldComponentContext.fieldCoordinates,
                fieldComponentContext.path
            )
            putConnectedPathForCanonicalPath(
                fieldComponentContext.canonicalPath,
                fieldComponentContext.path
            )
            this.apply {
                fsfc.featureCalculator.featureStoreName
                    .toOption()
                    .filterNot(FeatureCalculator.FEATURE_STORE_NOT_PROVIDED::equals)
                    .flatMap { storeName: String ->
                        connectorContext.materializationMetamodel.featureEngineeringModel
                            .featureJsonValueStoresByName
                            .getOrNone(storeName)
                    }
                    .tap { fjvs: FeatureJsonValueStore ->
                        putFeatureJsonValueStoreForPath(fieldComponentContext.path, fjvs)
                    }
                fsfc.featureCalculator.featurePublisherName
                    .toOption()
                    .filterNot(FeatureCalculator.FEATURE_PUBLISHER_NOT_PROVIDED::equals)
                    .flatMap { publisherName: String ->
                        connectorContext.materializationMetamodel.featureEngineeringModel
                            .featureJsonValuePublishersByName
                            .getOrNone(publisherName)
                    }
                    .tap { fjvp: FeatureJsonValuePublisher ->
                        putFeatureJsonValuePublisherForPath(fieldComponentContext.path, fjvp)
                    }
            }
        }
    }
}
