package funcify.feature.materializer.graph.connector

import arrow.core.*
import funcify.feature.error.ServiceError
import funcify.feature.graph.DirectedPersistentGraph
import funcify.feature.materializer.graph.MaterializationEdge
import funcify.feature.materializer.graph.component.QueryComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContext.ArgumentComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContext.FieldComponentContext
import funcify.feature.materializer.graph.context.StandardQuery
import funcify.feature.materializer.model.MaterializationMetamodel
import funcify.feature.schema.dataelement.DataElementCallable
import funcify.feature.schema.dataelement.DomainSpecifiedDataElementSource
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
import funcify.feature.tools.extensions.PersistentMapExtensions.reducePairsToPersistentMap
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.foldIntoTry
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.introspection.Introspection
import graphql.language.Argument
import graphql.language.ArrayValue
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import org.slf4j.Logger

/**
 * @author smccarron
 * @created 2023-08-05
 */
internal object StandardQueryConnector : RequestMaterializationGraphConnector<StandardQuery> {

    private val logger: Logger = loggerFor<StandardQueryConnector>()
    private val camelCaseTranslator: (String) -> String by lazy {
        val cache: ConcurrentMap<String, String> = ConcurrentHashMap()
        val convertToCamelCase: (String) -> String = { str: String ->
            when {
                str.indexOf('_') >= 0 || str.indexOf(' ') >= 0 -> {
                    str.splitToSequence(' ', '_')
                        .filter(String::isNotBlank)
                        .fold(StringBuilder()) { sb: StringBuilder, s: String ->
                            when {
                                sb.length == 0 && s.first().isUpperCase() -> {
                                    sb.append(s.replaceFirstChar { c: Char -> c.lowercase() })
                                }
                                sb.length != 0 && s.first().isLowerCase() -> {
                                    sb.append(s.replaceFirstChar { c: Char -> c.uppercase() })
                                }
                                else -> {
                                    sb.append(s)
                                }
                            }
                        }
                        .toString()
                }
                else -> {
                    str
                }
            }
        }
        { str: String -> cache.computeIfAbsent(str, convertToCamelCase) }
    }

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
                throw ServiceError.of("subdomain domain element arguments not yet handled")
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
            argumentComponentContext.argument.value
                .toOption()
                .filterIsInstance<VariableReference>()
                .isDefined() -> {
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
                // case 1: arg default value same as this arg value
                ga.argumentDefaultValue
                    .toOption()
                    .filter(InputValueWithState::isLiteral)
                    .filter(InputValueWithState::isSet)
                    .mapNotNull(InputValueWithState::getValue)
                    .filterIsInstance<Value<*>>()
                    .flatMap { v: Value<*> ->
                        argumentComponentContext.argument.toOption().filter { a: Argument ->
                            v == a.value
                        }
                    }
                    .orElse {
                        // case 2: arg type is nullable and this arg value is null
                        ga.type
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
                        ga.type
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
            .isDefined()
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
            getCoordinatesOfConnectedDataElementOrFeatureFieldMatchingArgument(
                    connectorContext,
                    argumentComponentContext
                )
                .isNotEmpty() -> {
                connectFeatureFieldArgumentToDataElementFieldOnAlreadyConnectedDomainDataElementSourceOrFeatureField(
                    connectorContext,
                    argumentComponentContext
                )
            }
            getCoordinatesOfUnconnectedFeatureFieldMatchingArgument(
                    connectorContext,
                    argumentComponentContext
                )
                .isDefined() -> {
                connectFeatureFieldArgumentToUnconnectedFeatureField(
                    connectorContext,
                    argumentComponentContext
                )
            }
            getFirstCoordinatesToConnectedDataElementSourcePathsPairOfUnconnectedDataElementFieldMatchingArgument(
                    connectorContext,
                    argumentComponentContext
                )
                .isDefined() -> {
                connectFeatureFieldArgumentToUnconnectedDataElementFieldOnConnectedDataElementSource(
                    connectorContext,
                    argumentComponentContext
                )
            }
            getFirstCoordinatesToUnconnectedRawInputDataElementSourcePathPairsOfUnconnectedDataElementFieldMatchingArgument(
                    connectorContext,
                    argumentComponentContext
                )
                .isDefined() -> {
                connectFeatureFieldArgumentToUnconnectedDataElementOnUnconnectedRawInputDataElementSource(
                    connectorContext,
                    argumentComponentContext
                )
            }
            getCoordinatesToUnconnectedDataElementSourceFieldsWithCompleteVariableInputSetsMatchingArgument(
                    connectorContext,
                    argumentComponentContext
                )
                .isDefined() -> {
                connectFeatureFieldArgumentToUnconnectedDataElementOnUnconnectedDataElementSourceWithCompleteVariableInputSet(
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

    private fun getCoordinatesOfConnectedDataElementOrFeatureFieldMatchingArgument(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext,
    ): ImmutableSet<FieldCoordinates> {
        return argumentComponentContext.argument
            .toOption()
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
                            .filter(connectorContext.connectedFieldPathsByCoordinates::containsKey)
                            .map(::persistentSetOf)
                    }
                    .orElse {
                        connectorContext.materializationMetamodel.aliasCoordinatesRegistry
                            .getFieldsWithAlias(n)
                            .asSequence()
                            .filter(connectorContext.connectedFieldPathsByCoordinates::containsKey)
                            .toPersistentSet()
                            .toOption()
                            .filter(ImmutableSet<FieldCoordinates>::isNotEmpty)
                    }
            }
            .fold(::persistentSetOf, ::identity)
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
            getCoordinatesOfConnectedDataElementOrFeatureFieldMatchingArgument(
                connectorContext,
                argumentComponentContext
            )
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

    private fun getCoordinatesOfUnconnectedFeatureFieldMatchingArgument(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext
    ): Option<FieldCoordinates> {
        return connectorContext.materializationMetamodel.featureCoordinatesByName
            .getOrNone(argumentComponentContext.argument.name)
            .orElse {
                connectorContext.materializationMetamodel.aliasCoordinatesRegistry
                    .getFieldsWithAlias(argumentComponentContext.argument.name)
                    .firstOrNone(
                        connectorContext.materializationMetamodel
                            .featureSpecifiedFeatureCalculatorsByCoordinates::containsKey
                    )
            }
    }

    private fun connectFeatureFieldArgumentToUnconnectedFeatureField(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext,
    ): StandardQuery {
        val fc: FieldCoordinates =
            getCoordinatesOfUnconnectedFeatureFieldMatchingArgument(
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
                    putConnectedPathForCanonicalPath(
                        argumentComponentContext.canonicalPath,
                        argumentComponentContext.path
                    )
                }
            }
            .orElseThrow()
    }

    private fun getFirstCoordinatesToConnectedDataElementSourcePathsPairOfUnconnectedDataElementFieldMatchingArgument(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext
    ): Option<Pair<FieldCoordinates, List<GQLOperationPath>>> {
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
                // TODO: Consider whether this operation is cacheable
                connectorContext.dataElementCallableBuildersByPath.keys
                    .asSequence()
                    .flatMap { p: GQLOperationPath ->
                        when {
                            connectorContext.canonicalPathByConnectedPath
                                .getOrNone(p)
                                .filter { cp: GQLOperationPath ->
                                    connectorContext.materializationMetamodel
                                        .fieldCoordinatesAvailableUnderPath
                                        .invoke(fc, cp)
                                }
                                .isDefined() -> {
                                sequenceOf(p)
                            }
                            else -> {
                                emptySequence()
                            }
                        }
                    }
                    .toList()
                    .let { ps: List<GQLOperationPath> ->
                        if (ps.isNotEmpty()) {
                            sequenceOf(fc to ps)
                        } else {
                            emptySequence()
                        }
                    }
            }
            .firstOrNone()
    }

    private fun connectFeatureFieldArgumentToUnconnectedDataElementFieldOnConnectedDataElementSource(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext
    ): StandardQuery {
        val (fc: FieldCoordinates, dsdesps: List<GQLOperationPath>) =
            getFirstCoordinatesToConnectedDataElementSourcePathsPairOfUnconnectedDataElementFieldMatchingArgument(
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
            .map { p: GQLOperationPath ->
                connectorContext.canonicalPathByConnectedPath.getOrNone(p).map {
                    cp: GQLOperationPath ->
                    cp to p
                }
            }
            .flatMapOptions()
            .foldIntoTry(connectorContext) {
                sq: StandardQuery,
                (
                    canonicalDomainDataElementPath: GQLOperationPath,
                    domainDataElementPath: GQLOperationPath) ->
                connectFeatureArgumentToSpecificDataElementFieldWithinConnectedDomainDataElementSource(
                    sq,
                    canonicalDomainDataElementPath,
                    domainDataElementPath,
                    fc,
                    featureFieldPath,
                    argumentComponentContext
                )
            }
            .orElseThrow()
    }

    private fun connectFeatureArgumentToSpecificDataElementFieldWithinConnectedDomainDataElementSource(
        connectorContext: StandardQuery,
        canonicalDomainPath: GQLOperationPath,
        domainPath: GQLOperationPath,
        dataElementFieldCoordinates: FieldCoordinates,
        featureFieldPath: GQLOperationPath,
        argumentComponentContext: ArgumentComponentContext,
    ): StandardQuery {
        val childDataElementFieldCanonicalPath: GQLOperationPath =
            connectorContext.materializationMetamodel.firstPathWithFieldCoordinatesUnderPath
                .invoke(dataElementFieldCoordinates, canonicalDomainPath)
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
                        dataElementFieldCoordinates,
                        canonicalDomainPath
                    )
                }
                .orElseThrow()
        val childDataElementFieldPathToConnect: GQLOperationPath =
            domainPath.transform {
                appendSelections(
                    childDataElementFieldCanonicalPath.selection.subList(
                        domainPath.selection.size,
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
        return Try.success(connectorContext)
            .map { sq: StandardQuery ->
                ((canonicalDomainPath.selection.size + 1) until
                        childDataElementFieldCanonicalPath.selection.size)
                    .asSequence()
                    .fold(sq) { sq1: StandardQuery, limit: Int ->
                        val p: GQLOperationPath =
                            GQLOperationPath.of {
                                selections(
                                    childDataElementFieldPathToConnect.selection.subList(0, limit)
                                )
                            }
                        when {
                            p in sq1.canonicalPathByConnectedPath -> {
                                sq1
                            }
                            else -> {
                                val cp: GQLOperationPath =
                                    GQLOperationPath.of {
                                        selections(
                                            childDataElementFieldCanonicalPath.selection.subList(
                                                0,
                                                limit
                                            )
                                        )
                                    }
                                logger.debug(
                                    "connect_parent_or_ancestor_of_data_element_field: [ cp: {}, p: {} ]",
                                    cp,
                                    p
                                )
                                sq1.materializationMetamodel.fieldCoordinatesByPath
                                    .getOrNone(cp)
                                    .flatMap(ImmutableSet<FieldCoordinates>::firstOrNone)
                                    .map { fc1: FieldCoordinates ->
                                        sq1.queryComponentContextFactory
                                            .fieldComponentContextBuilder()
                                            .field(Field.newField().name(fc1.fieldName).build())
                                            .fieldCoordinates(fc1)
                                            .path(p)
                                            .canonicalPath(cp)
                                            .build()
                                    }
                                    .successIfDefined {
                                        ServiceError.of(
                                            "[ path: %s ] does not match known field_coordinates",
                                            p
                                        )
                                    }
                                    .map { sfcc: FieldComponentContext -> connectField(sq1, sfcc) }
                                    .orElseThrow()
                            }
                        }
                    }
            }
            .map { sq: StandardQuery ->
                val childDataElementFieldComponentContext: FieldComponentContext =
                    sq.queryComponentContextFactory
                        .fieldComponentContextBuilder()
                        .fieldCoordinates(dataElementFieldCoordinates)
                        .path(childDataElementFieldPathToConnect)
                        .canonicalPath(childDataElementFieldCanonicalPath)
                        .field(Field.newField().name(dataElementFieldCoordinates.fieldName).build())
                        .build()
                connectField(sq, childDataElementFieldComponentContext)
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
                                    childDataElementFieldCanonicalPath,
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

    private fun getFirstCoordinatesToUnconnectedRawInputDataElementSourcePathPairsOfUnconnectedDataElementFieldMatchingArgument(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext
    ): Option<Pair<FieldCoordinates, List<GQLOperationPath>>> {
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
                connectorContext.materializationMetamodel.domainSpecifiedDataElementSourceByPath
                    .asSequence()
                    .filter { (p: GQLOperationPath, dsdes: DomainSpecifiedDataElementSource) ->
                        dsdes.domainFieldCoordinates.fieldName in
                            connectorContext.rawInputContextKeys &&
                            connectorContext.materializationMetamodel
                                .fieldCoordinatesAvailableUnderPath
                                .invoke(fc, p) &&
                            p !in connectorContext.connectedPathsByCanonicalPath
                    }
                    .map(Map.Entry<GQLOperationPath, DomainSpecifiedDataElementSource>::key)
                    .toList()
                    .let { ps: List<GQLOperationPath> ->
                        if (ps.isNotEmpty()) {
                            sequenceOf(fc to ps)
                        } else {
                            emptySequence()
                        }
                    }
            }
            .firstOrNone()
    }

    private fun connectFeatureFieldArgumentToUnconnectedDataElementOnUnconnectedRawInputDataElementSource(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext
    ): StandardQuery {
        val (fc: FieldCoordinates, dsdesps: List<GQLOperationPath>) =
            getFirstCoordinatesToUnconnectedRawInputDataElementSourcePathPairsOfUnconnectedDataElementFieldMatchingArgument(
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

        return dsdesps
            .asSequence()
            .map { p: GQLOperationPath ->
                connectorContext.materializationMetamodel.domainSpecifiedDataElementSourceByPath
                    .getOrNone(p)
            }
            .flatMapOptions()
            .fold(connectorContext) { ctx: StandardQuery, dsdes: DomainSpecifiedDataElementSource ->
                val childPath: GQLOperationPath =
                    ctx.materializationMetamodel.firstPathWithFieldCoordinatesUnderPath
                        .invoke(fc, dsdes.domainPath)
                        .filter { p: GQLOperationPath ->
                            ctx.materializationMetamodel.dataElementElementTypePath.isAncestorTo(p)
                        }
                        .successIfDefined {
                            ServiceError.of(
                                """path mapping to field_coordinates [ %s ] 
                                |under domain_data_element_source [ path: %s ] 
                                |expected but not found"""
                                    .flatten(),
                                fc,
                                dsdes.domainPath
                            )
                        }
                        .orElseThrow()
                Try.success(ctx)
                    .map { sq: StandardQuery ->
                        when (sq.materializationMetamodel.dataElementElementTypePath) {
                            !in sq.connectedPathsByCanonicalPath -> {
                                val dataElementElementTypeFieldComponentContext:
                                    FieldComponentContext =
                                    sq.queryComponentContextFactory
                                        .fieldComponentContextBuilder()
                                        .field(
                                            Field.newField()
                                                .name(
                                                    sq.materializationMetamodel
                                                        .featureEngineeringModel
                                                        .dataElementFieldCoordinates
                                                        .fieldName
                                                )
                                                .build()
                                        )
                                        .fieldCoordinates(
                                            sq.materializationMetamodel.featureEngineeringModel
                                                .dataElementFieldCoordinates
                                        )
                                        .canonicalPath(
                                            sq.materializationMetamodel.dataElementElementTypePath
                                        )
                                        .build()
                                connectField(sq, dataElementElementTypeFieldComponentContext)
                            }
                            else -> {
                                sq
                            }
                        }
                    }
                    .map { sq: StandardQuery ->
                        val domainDataElementFieldContext: FieldComponentContext =
                            sq.queryComponentContextFactory
                                .fieldComponentContextBuilder()
                                .path(dsdes.domainPath)
                                .field(
                                    Field.newField()
                                        .name(dsdes.domainFieldCoordinates.fieldName)
                                        .build()
                                )
                                .canonicalPath(dsdes.domainPath)
                                .fieldCoordinates(dsdes.domainFieldCoordinates)
                                .build()
                        connectField(sq, domainDataElementFieldContext)
                    }
                    .map { sq: StandardQuery ->
                        dsdes.allArgumentsByPath.asSequence().fold(sq) {
                            sq1: StandardQuery,
                            (p: GQLOperationPath, a: GraphQLArgument) ->
                            val facc: ArgumentComponentContext =
                                sq1.queryComponentContextFactory
                                    .argumentComponentContextBuilder()
                                    .path(p)
                                    .canonicalPath(p)
                                    .argument(
                                        Argument.newArgument()
                                            .name(a.name)
                                            .value(
                                                a.argumentDefaultValue.value
                                                    .toOption()
                                                    .filterIsInstance<Value<*>>()
                                                    .getOrElse { NullValue.of() }
                                            )
                                            .build()
                                    )
                                    .fieldCoordinates(dsdes.domainFieldCoordinates)
                                    .build()
                            connectArgument(sq1, facc)
                        }
                    }
                    .map { sq: StandardQuery ->
                        ((dsdes.domainPath.selection.size + 1) until childPath.selection.size)
                            .asSequence()
                            .map { limit: Int ->
                                // Note: Does not start with possibly aliased or fragment-spread
                                // nested data_element element_type paths
                                GQLOperationPath.of {
                                    selections(childPath.selection.subList(0, limit))
                                }
                            }
                            .fold(sq) { sq1: StandardQuery, p: GQLOperationPath ->
                                when {
                                    p in sq1.connectedPathsByCanonicalPath -> {
                                        sq1
                                    }
                                    else -> {
                                        sq1.materializationMetamodel.fieldCoordinatesByPath
                                            .getOrNone(p)
                                            .flatMap(ImmutableSet<FieldCoordinates>::firstOrNone)
                                            .map { fc1: FieldCoordinates ->
                                                sq1.queryComponentContextFactory
                                                    .fieldComponentContextBuilder()
                                                    .field(
                                                        Field.newField().name(fc1.fieldName).build()
                                                    )
                                                    .fieldCoordinates(fc1)
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
                                            .map { sfcc: FieldComponentContext ->
                                                connectField(sq1, sfcc)
                                            }
                                            .orElseThrow()
                                    }
                                }
                            }
                    }
                    .map { sq: StandardQuery ->
                        val childDataElementFieldComponentContext: FieldComponentContext =
                            sq.queryComponentContextFactory
                                .fieldComponentContextBuilder()
                                .fieldCoordinates(fc)
                                .path(childPath)
                                .canonicalPath(childPath)
                                .field(Field.newField().name(fc.fieldName).build())
                                .build()
                        connectField(sq, childDataElementFieldComponentContext)
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
                                            fieldPath,
                                            argumentComponentContext.path,
                                            MaterializationEdge.EXTRACT_FROM_SOURCE
                                        )
                                        .putEdge(
                                            argumentComponentContext.path,
                                            childPath,
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
    }

    private fun getCoordinatesToUnconnectedDataElementSourceFieldsWithCompleteVariableInputSetsMatchingArgument(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext,
    ): Option<Triple<GQLOperationPath, List<Pair<String, GQLOperationPath>>, FieldCoordinates>> {
        return when {
            connectorContext.variableKeys.isEmpty() -> {
                none()
            }
            else -> {
                connectorContext.variableKeys
                    .asSequence()
                    .map { vk: String ->
                        connectorContext.materializationMetamodel.dataElementPathByFieldArgumentName
                            .getOrNone(vk)
                            .orElse {
                                connectorContext.materializationMetamodel.aliasCoordinatesRegistry
                                    .getFieldArgumentsWithAlias(vk)
                                    .asSequence()
                                    .map { argLoc: Pair<FieldCoordinates, String> ->
                                        connectorContext.materializationMetamodel
                                            .domainSpecifiedDataElementSourceArgumentPathsByArgLocation
                                            .getOrNone(argLoc)
                                    }
                                    .flatMapOptions()
                                    .toPersistentSet()
                                    .toOption()
                                    .filter(ImmutableSet<GQLOperationPath>::isNotEmpty)
                            }
                            .map { paths: ImmutableSet<GQLOperationPath> -> vk to paths }
                    }
                    .flatMapOptions()
                    .flatMap { (vk: String, ps: ImmutableSet<GQLOperationPath>) ->
                        ps.asSequence().map { p: GQLOperationPath -> vk to p }
                    }
                    .groupBy { (_: String, p: GQLOperationPath) ->
                        p.getParentPath().getOrElse { GQLOperationPath.getRootPath() }
                    }
                    .asSequence()
                    .map { (dp: GQLOperationPath, vkArg: List<Pair<String, GQLOperationPath>>) ->
                        val providedArgPaths: ImmutableSet<GQLOperationPath> =
                            vkArg
                                .asSequence()
                                .map { (_: String, ap: GQLOperationPath) -> ap }
                                .toPersistentSet()
                        connectorContext.materializationMetamodel
                            .domainSpecifiedDataElementSourceByPath
                            .getOrNone(dp)
                            .filter { dsdes: DomainSpecifiedDataElementSource ->
                                dsdes.domainArgumentsWithoutDefaultValuesByPath.keys.all(
                                    providedArgPaths::contains
                                )
                            }
                            .map { dsdes: DomainSpecifiedDataElementSource ->
                                dsdes.domainPath to vkArg
                            }
                    }
                    .flatMapOptions()
                    .reducePairsToPersistentMap()
                    .let { dpToVkArgs: Map<GQLOperationPath, List<Pair<String, GQLOperationPath>>>
                        ->
                        // logger.debug(
                        //    "domain_path_to_variable_key_arg_paths: [ {} ]",
                        //    dpToVkArgs.asSequence().joinToString(",\n", "{ ", " }")
                        // )
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
                            .map { fc: FieldCoordinates ->
                                // TODO: Should the first match be taken or all matches? TBD
                                dpToVkArgs.keys
                                    .asSequence()
                                    .firstOrNone { dp: GQLOperationPath ->
                                        connectorContext.materializationMetamodel
                                            .fieldCoordinatesAvailableUnderPath
                                            .invoke(fc, dp)
                                    }
                                    .map { dp: GQLOperationPath ->
                                        Triple(dp, dpToVkArgs[dp]!!, fc)
                                    }
                            }
                            .flatMapOptions()
                            .firstOrNone()
                    }
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
                    .getFieldsWithAlias(camelCaseTranslator(argumentComponentContext.argument.name))
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

    private fun connectFeatureFieldArgumentToUnconnectedDataElementOnUnconnectedDataElementSourceWithCompleteVariableInputSet(
        connectorContext: StandardQuery,
        argumentComponentContext: ArgumentComponentContext,
    ): StandardQuery {
        val (
            domainPath: GQLOperationPath,
            variablesToArgPaths: List<Pair<String, GQLOperationPath>>,
            dataElementCoordinates: FieldCoordinates) =
            getCoordinatesToUnconnectedDataElementSourceFieldsWithCompleteVariableInputSetsMatchingArgument(
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
            |variable_keys_to_arg_paths: {}, 
            |data_element_field_coordinates: {} ]"""
                .flatten(),
            domainPath,
            variablesToArgPaths,
            dataElementCoordinates
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
        val dsdes: DomainSpecifiedDataElementSource =
            connectorContext.materializationMetamodel.domainSpecifiedDataElementSourceByPath
                .getOrNone(domainPath)
                .successIfDefined {
                    ServiceError.of(
                        "%s expected but not found at [ path: %s ]",
                        DomainSpecifiedDataElementSource::class.simpleName,
                        domainPath
                    )
                }
                .orElseThrow()
        val childPath: GQLOperationPath =
            connectorContext.materializationMetamodel.firstPathWithFieldCoordinatesUnderPath
                .invoke(dataElementCoordinates, domainPath)
                .successIfDefined {
                    ServiceError.of(
                        "path under which field coordinates [ %s ] available under [ path: %s ]",
                        dataElementCoordinates,
                        domainPath
                    )
                }
                .orElseThrow()
        return Try.success(connectorContext)
            .map { sq: StandardQuery ->
                val fieldContext: FieldComponentContext =
                    connectorContext.queryComponentContextFactory
                        .fieldComponentContextBuilder()
                        .field(
                            Field.newField().name(dsdes.domainFieldCoordinates.fieldName).build()
                        )
                        .fieldCoordinates(dsdes.domainFieldCoordinates)
                        .path(dsdes.domainPath)
                        .canonicalPath(dsdes.domainPath)
                        .build()
                connectField(sq, fieldContext)
            }
            .map { sq: StandardQuery ->
                variablesToArgPaths
                    .asSequence()
                    .map { (vk: String, ap: GQLOperationPath) ->
                        dsdes.allArgumentsByPath.getOrNone(ap).map { ga: GraphQLArgument ->
                            sq.queryComponentContextFactory
                                .argumentComponentContextBuilder()
                                .argument(
                                    Argument.newArgument()
                                        .name(ga.name)
                                        .value(
                                            VariableReference.newVariableReference()
                                                .name(vk)
                                                .build()
                                        )
                                        .build()
                                )
                                .fieldCoordinates(dsdes.domainFieldCoordinates)
                                .path(ap)
                                .canonicalPath(ap)
                                .build()
                        }
                    }
                    .flatMapOptions()
                    .fold(sq) { sq1: StandardQuery, acc: ArgumentComponentContext ->
                        connectArgument(sq1, acc)
                    }
            }
            .map { sq: StandardQuery ->
                (domainPath.selection.size + 1)
                    .until(childPath.selection.size)
                    .asSequence()
                    .map { limit: Int ->
                        when {
                            limit == childPath.selection.size -> childPath
                            else ->
                                GQLOperationPath.of {
                                    appendSelections(childPath.selection.subList(0, limit))
                                }
                        }
                    }
                    .map { cp: GQLOperationPath ->
                        sq.materializationMetamodel.fieldCoordinatesByPath
                            .getOrNone(cp)
                            .map(ImmutableSet<FieldCoordinates>::asSequence)
                            .getOrElse(::emptySequence)
                            .firstOrNone()
                            .map { fc: FieldCoordinates -> cp to fc }
                    }
                    .flatMapOptions()
                    .fold(sq) { sq1: StandardQuery, (cp: GQLOperationPath, fc: FieldCoordinates) ->
                        logger.debug(
                            "connecting unconnected_data_element_field with complete variable set: [ fc: {}, cp: {} ]",
                            fc,
                            cp
                        )
                        val fieldContext: FieldComponentContext =
                            connectorContext.queryComponentContextFactory
                                .fieldComponentContextBuilder()
                                .field(Field.newField().name(fc.fieldName).build())
                                .fieldCoordinates(fc)
                                .path(cp)
                                .canonicalPath(cp)
                                .build()
                        connectField(sq1, fieldContext)
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
                                childPath,
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
