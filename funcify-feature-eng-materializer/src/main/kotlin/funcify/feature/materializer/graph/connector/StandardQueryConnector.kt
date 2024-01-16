package funcify.feature.materializer.graph.connector

import arrow.core.*
import funcify.feature.error.ServiceError
import funcify.feature.graph.DirectedPersistentGraph
import funcify.feature.materializer.graph.MaterializationEdge
import funcify.feature.materializer.graph.component.QueryComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContext.FieldArgumentComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContext.SelectedFieldComponentContext
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
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.introspection.Introspection
import graphql.language.Argument
import graphql.language.Field
import graphql.language.NullValue
import graphql.language.Value
import graphql.language.VariableReference
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLCompositeType
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLTypeUtil
import graphql.schema.InputValueWithState
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

    override fun connectFieldArgument(
        connectorContext: StandardQuery,
        fieldArgumentComponentContext: FieldArgumentComponentContext,
    ): StandardQuery {
        logger.debug(
            "connect_field_argument: [ field_argument_component_context: { path: {}, coordinates: {}, canonical_path: {} } ]",
            fieldArgumentComponentContext.path,
            fieldArgumentComponentContext.fieldCoordinates,
            fieldArgumentComponentContext.canonicalPath
        )
        return when {
            fieldArgumentComponentContext.fieldCoordinates in
                connectorContext.materializationMetamodel.elementTypeCoordinates -> {
                throw ServiceError.of("no arguments are permitted on element_type fields")
            }
            connectorContext.materializationMetamodel.dataElementElementTypePath.isAncestorTo(
                fieldArgumentComponentContext.canonicalPath
            ) -> {
                connectSelectedDataElementArgument(connectorContext, fieldArgumentComponentContext)
            }
            connectorContext.materializationMetamodel.featureElementTypePath.isAncestorTo(
                fieldArgumentComponentContext.canonicalPath
            ) -> {
                connectSelectedFeatureArgument(connectorContext, fieldArgumentComponentContext)
            }
            connectorContext.materializationMetamodel.transformerElementTypePath.isAncestorTo(
                fieldArgumentComponentContext.canonicalPath
            ) -> {
                connectSelectedTransformerArgument(connectorContext, fieldArgumentComponentContext)
            }
            else -> {
                throw ServiceError.of(
                    "unable to identify element_type bucket for selected_field_argument [ path: %s ]",
                    fieldArgumentComponentContext.path
                )
            }
        }
    }

    private fun connectSelectedDataElementArgument(
        connectorContext: StandardQuery,
        fieldArgumentComponentContext: FieldArgumentComponentContext
    ): StandardQuery {
        return when {
            // Case 1: Already connected
            connectorContext.requestGraph
                .edgesFromPoint(fieldArgumentComponentContext.path)
                .any() ||
                connectorContext.requestGraph
                    .edgesToPoint(fieldArgumentComponentContext.path)
                    .any() -> {
                connectorContext
            }
            // Case 2: Domain Data Element Argument
            fieldArgumentComponentContext.fieldCoordinates in
                connectorContext.materializationMetamodel
                    .domainSpecifiedDataElementSourceByCoordinates -> {
                connectorContext.update(
                    connectDomainDataElementFieldToArgument(
                        connectorContext,
                        fieldArgumentComponentContext
                    )
                )
            }
            // Case 3: Subdomain Data Element Argument
            else -> {
                throw ServiceError.of("subdomain domain element arguments not yet handled")
            }
        }
    }

    private fun connectDomainDataElementFieldToArgument(
        connectorContext: StandardQuery,
        fieldArgumentComponentContext: FieldArgumentComponentContext
    ): (StandardQuery.Builder) -> StandardQuery.Builder {
        return { sqb: StandardQuery.Builder ->
            val a: Argument = fieldArgumentComponentContext.argument
            val fieldPath: GQLOperationPath =
                fieldArgumentComponentContext.path
                    .getParentPath()
                    .filter { pp: GQLOperationPath -> connectorContext.requestGraph.contains(pp) }
                    .successIfDefined {
                        ServiceError.of(
                            """domain data_element source field has not 
                                |been defined in request_materialization_graph 
                                |for [ path: %s ]"""
                                .flatten(),
                            fieldArgumentComponentContext.path.getParentPath().orNull()
                        )
                    }
                    .orElseThrow()
            val faLoc: Pair<FieldCoordinates, String> =
                fieldArgumentComponentContext.fieldCoordinates to a.name
            val e: MaterializationEdge =
                when {
                    // Case 1: Raw input context contains entry for parent field or alias of parent
                    // field
                    connectorContext.requestGraph
                        .get(fieldPath)
                        .toOption()
                        .filterIsInstance<SelectedFieldComponentContext>()
                        .map(SelectedFieldComponentContext::fieldCoordinates)
                        .map(FieldCoordinates::getFieldName)
                        .filter(connectorContext.rawInputContextKeys::contains)
                        .orElse {
                            connectorContext.materializationMetamodel.aliasCoordinatesRegistry
                                .getAllAliasesForField(faLoc.first)
                                .asSequence()
                                .filter(connectorContext.rawInputContextKeys::contains)
                                .firstOrNone()
                        }
                        .isDefined() -> {
                        MaterializationEdge.RAW_INPUT_VALUE_PROVIDED
                    }
                    // Case 2: Raw input context contains entry for field argument that is set to
                    // its default argument value or alias thereof
                    fieldArgumentComponentContext.argument.value
                        .toOption()
                        .filterNot(VariableReference::class::isInstance)
                        .and(
                            connectorContext.materializationMetamodel
                                .domainSpecifiedDataElementSourceByCoordinates
                                .getOrNone(faLoc.first)
                                .map(
                                    DomainSpecifiedDataElementSource::
                                        argumentsWithDefaultValuesByName
                                )
                                .flatMap { awdvn: ImmutableMap<String, GraphQLArgument> ->
                                    awdvn.getOrNone(fieldArgumentComponentContext.argument.name)
                                }
                                .filter { ga: GraphQLArgument ->
                                    fieldArgumentComponentContext.argument.value ==
                                        ga.argumentDefaultValue.value
                                }
                        )
                        .and(
                            a.name
                                .toOption()
                                .filter(connectorContext.rawInputContextKeys::contains)
                                .orElse {
                                    connectorContext.materializationMetamodel
                                        .aliasCoordinatesRegistry
                                        .getAllAliasesForFieldArgument(faLoc)
                                        .asSequence()
                                        .filter(connectorContext.rawInputContextKeys::contains)
                                        .firstOrNone()
                                }
                        )
                        .isDefined() -> {
                        MaterializationEdge.RAW_INPUT_VALUE_PROVIDED
                    }
                    // Case 3: Variable key matches variable reference for argument
                    fieldArgumentComponentContext.argument.value
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
                    a.value
                        .toOption()
                        .filterNot(VariableReference::class::isInstance)
                        .and(
                            connectorContext.materializationMetamodel
                                .domainSpecifiedDataElementSourceByCoordinates
                                .getOrNone(faLoc.first)
                                .map(
                                    DomainSpecifiedDataElementSource::
                                        argumentsWithoutDefaultValuesByName
                                )
                                .filter { awdvn: ImmutableMap<String, GraphQLArgument> ->
                                    awdvn.containsKey(a.name)
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
                    a.value
                        .toOption()
                        .filterNot(VariableReference::class::isInstance)
                        .and(
                            connectorContext.materializationMetamodel
                                .domainSpecifiedDataElementSourceByCoordinates
                                .getOrNone(faLoc.first)
                                .map(
                                    DomainSpecifiedDataElementSource::
                                        argumentsWithDefaultValuesByName
                                )
                                .flatMap { awdvn: ImmutableMap<String, GraphQLArgument> ->
                                    awdvn.getOrNone(a.name)
                                }
                                .filter { ga: GraphQLArgument ->
                                    a.value != ga.argumentDefaultValue.value
                                }
                        )
                        .isDefined() -> {
                        MaterializationEdge.DIRECT_ARGUMENT_VALUE_PROVIDED
                    }
                    // Case 6: Argument value is not a variable reference and default argument value
                    // provided for argument
                    a.value
                        .toOption()
                        .filterNot(VariableReference::class::isInstance)
                        .and(
                            connectorContext.materializationMetamodel
                                .domainSpecifiedDataElementSourceByCoordinates
                                .getOrNone(faLoc.first)
                                .map(
                                    DomainSpecifiedDataElementSource::
                                        argumentsWithDefaultValuesByName
                                )
                                .filter { awodvn: ImmutableMap<String, GraphQLArgument> ->
                                    awodvn.containsKey(a.name)
                                }
                        )
                        .isDefined() -> {
                        MaterializationEdge.DEFAULT_ARGUMENT_VALUE_PROVIDED
                    }
                    else -> {
                        throw ServiceError.of(
                            "edge type could not be determined for [ field_path %s to argument_path %s ]",
                            fieldPath,
                            fieldArgumentComponentContext.path
                        )
                    }
                }
            // Connect the field to its argument: field is dependent on argument
            // Connect the argument to its element type
            sqb.requestGraph(
                    connectorContext.requestGraph
                        .put(fieldArgumentComponentContext.path, fieldArgumentComponentContext)
                        .putEdge(fieldPath, fieldArgumentComponentContext.path, e)
                        .putEdge(
                            fieldArgumentComponentContext.path,
                            connectorContext.materializationMetamodel.dataElementElementTypePath,
                            MaterializationEdge.ELEMENT_TYPE
                        )
                )
                .putConnectedPathForCanonicalPath(
                    fieldArgumentComponentContext.canonicalPath,
                    fieldArgumentComponentContext.path
                )
        }
    }

    private fun connectSelectedFeatureArgument(
        connectorContext: StandardQuery,
        fieldArgumentComponentContext: FieldArgumentComponentContext
    ): StandardQuery {
        logger.debug(
            "connect_selected_feature_argument: [ field_argument_component_context.path: {} ]",
            fieldArgumentComponentContext.path
        )
        return when {
            // Case 1: Already connected to data_element_field
            connectorContext.requestGraph
                .edgesFromPoint(fieldArgumentComponentContext.path)
                .any() -> {
                connectorContext
            }
            fieldArgumentComponentContext.argument.value
                .toOption()
                .filterIsInstance<VariableReference>()
                .isDefined() -> {
                connectorContext.update(
                    connectFeatureFieldToVariableProvidedArgument(
                        connectorContext,
                        fieldArgumentComponentContext
                    )
                )
            }
            connectorContext.materializationMetamodel
                .featureSpecifiedFeatureCalculatorsByCoordinates
                .getOrNone(fieldArgumentComponentContext.fieldCoordinates)
                .flatMap { fsfc: FeatureSpecifiedFeatureCalculator ->
                    fsfc.argumentsByName.getOrNone(fieldArgumentComponentContext.argument.name)
                }
                .filter { ga: GraphQLArgument ->
                    ga.argumentDefaultValue.value == fieldArgumentComponentContext.argument.value
                }
                .isDefined() -> {
                connectFeatureFieldToFeatureArgumentToDataElementField(
                    connectorContext,
                    fieldArgumentComponentContext
                )
            }
            else -> {
                throw ServiceError.of("feature argument case not yet handled")
            }
        }
    }

    private fun connectFeatureFieldToVariableProvidedArgument(
        connectorContext: StandardQuery,
        fieldArgumentComponentContext: FieldArgumentComponentContext
    ): (StandardQuery.Builder) -> StandardQuery.Builder {
        return { sqb: StandardQuery.Builder ->
            val fieldPath: GQLOperationPath =
                fieldArgumentComponentContext.path
                    .getParentPath()
                    .filter { p: GQLOperationPath -> connectorContext.requestGraph.contains(p) }
                    .successIfDefined {
                        ServiceError.of(
                            "feature_field_argument parent field path [ %s ] not found in request_materialization_graph",
                            fieldArgumentComponentContext.path.getParentPath().orNull()
                        )
                    }
                    .orElseThrow()
            sqb.requestGraph(
                    connectorContext.requestGraph
                        .put(fieldArgumentComponentContext.path, fieldArgumentComponentContext)
                        .putEdge(
                            fieldPath,
                            fieldArgumentComponentContext.path,
                            MaterializationEdge.VARIABLE_VALUE_PROVIDED
                        )
                )
                .putConnectedPathForCanonicalPath(
                    fieldArgumentComponentContext.canonicalPath,
                    fieldArgumentComponentContext.path
                )
        }
    }

    private fun connectFeatureFieldToFeatureArgumentToDataElementField(
        connectorContext: StandardQuery,
        fieldArgumentComponentContext: FieldArgumentComponentContext
    ): StandardQuery {
        logger.debug(
            "connect_feature_field_to_feature_argument_to_data_element_field: [ field_argument_component_context.path: {} ]",
            fieldArgumentComponentContext.path
        )
        return when {
            getCoordinatesOfConnectedDataElementOrFeatureFieldMatchingArgument(
                    connectorContext,
                    fieldArgumentComponentContext
                )
                .isNotEmpty() -> {
                connectorContext.update(
                    connectFeatureFieldArgumentToDataElementFieldOnAlreadyConnectedDomainDataElementSourceOrFeatureField(
                        connectorContext,
                        fieldArgumentComponentContext
                    )
                )
            }
            getCoordinatesOfUnconnectedFeatureFieldMatchingArgument(
                    connectorContext,
                    fieldArgumentComponentContext
                )
                .isDefined() -> {
                connectFeatureFieldArgumentToUnconnectedFeatureField(
                    connectorContext,
                    fieldArgumentComponentContext
                )
            }
            getFirstCoordinatesToConnectedDataElementSourcePathsPairOfUnconnectedDataElementFieldMatchingArgument(
                    connectorContext,
                    fieldArgumentComponentContext
                )
                .isDefined() -> {
                connectFeatureFieldArgumentToUnconnectedDataElementFieldOnConnectedDataElementSource(
                    connectorContext,
                    fieldArgumentComponentContext
                )
            }
            getFirstCoordinatesToUnconnectedRawInputDataElementSourcePathPairsOfUnconnectedDataElementFieldMatchingArgument(
                    connectorContext,
                    fieldArgumentComponentContext
                )
                .isDefined() -> {
                connectFeatureFieldArgumentToUnconnectedDataElementOnUnconnectedRawInputDataElementSource(
                    connectorContext,
                    fieldArgumentComponentContext
                )
            }
            // Next case: unconnected data_element_source with variable keys matching all of its
            // arguments
            else -> {
                throw ServiceError.of(
                    """unable to map feature_field_argument 
                    |[ argument: { path: %s, name: %s } ] 
                    |to data_element_field value"""
                        .flatten(),
                    fieldArgumentComponentContext.path,
                    fieldArgumentComponentContext.argument.name
                )
            }
        }
    }

    private fun getCoordinatesOfConnectedDataElementOrFeatureFieldMatchingArgument(
        connectorContext: StandardQuery,
        fieldArgumentComponentContext: FieldArgumentComponentContext,
    ): ImmutableSet<FieldCoordinates> {
        return fieldArgumentComponentContext.argument
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
        fieldArgumentComponentContext: FieldArgumentComponentContext
    ): (StandardQuery.Builder) -> StandardQuery.Builder {
        return { sqb: StandardQuery.Builder ->
            val fieldPath: GQLOperationPath =
                fieldArgumentComponentContext.path
                    .getParentPath()
                    .filter { p: GQLOperationPath -> connectorContext.requestGraph.contains(p) }
                    .successIfDefined {
                        ServiceError.of(
                            "feature_field_argument [ argument.name: %s ] parent field path [ %s ] not found in request_materialization_graph",
                            fieldArgumentComponentContext.argument.name,
                            fieldArgumentComponentContext.path.getParentPath().orNull()
                        )
                    }
                    .orElseThrow()
            val coordinatesOfConnectedField: ImmutableSet<FieldCoordinates> =
                getCoordinatesOfConnectedDataElementOrFeatureFieldMatchingArgument(
                    connectorContext,
                    fieldArgumentComponentContext
                )
            sqb.requestGraph(
                    coordinatesOfConnectedField
                        .asSequence()
                        .flatMap { fc: FieldCoordinates ->
                            connectorContext.connectedFieldPathsByCoordinates
                                .getOrNone(fc)
                                .fold(::emptySequence, ImmutableSet<GQLOperationPath>::asSequence)
                        }
                        .fold(
                            connectorContext.requestGraph
                                .put(
                                    fieldArgumentComponentContext.path,
                                    fieldArgumentComponentContext
                                )
                                .putEdge(
                                    fieldPath,
                                    fieldArgumentComponentContext.path,
                                    MaterializationEdge.EXTRACT_FROM_SOURCE
                                )
                        ) {
                            d:
                                DirectedPersistentGraph<
                                    GQLOperationPath, QueryComponentContext, MaterializationEdge
                                >,
                            p: GQLOperationPath ->
                            d.putEdge(
                                fieldArgumentComponentContext.path,
                                p,
                                MaterializationEdge.EXTRACT_FROM_SOURCE
                            )
                        }
                )
                .putConnectedPathForCanonicalPath(
                    fieldArgumentComponentContext.canonicalPath,
                    fieldArgumentComponentContext.path
                )
        }
    }

    private fun getCoordinatesOfUnconnectedFeatureFieldMatchingArgument(
        connectorContext: StandardQuery,
        fieldArgumentComponentContext: FieldArgumentComponentContext
    ): Option<FieldCoordinates> {
        return connectorContext.materializationMetamodel.featureCoordinatesByName
            .getOrNone(fieldArgumentComponentContext.argument.name)
            .orElse {
                connectorContext.materializationMetamodel.aliasCoordinatesRegistry
                    .getFieldsWithAlias(fieldArgumentComponentContext.argument.name)
                    .firstOrNone(
                        connectorContext.materializationMetamodel
                            .featureSpecifiedFeatureCalculatorsByCoordinates::containsKey
                    )
            }
    }

    private fun connectFeatureFieldArgumentToUnconnectedFeatureField(
        connectorContext: StandardQuery,
        fieldArgumentComponentContext: FieldArgumentComponentContext,
    ): StandardQuery {
        val fc: FieldCoordinates =
            getCoordinatesOfUnconnectedFeatureFieldMatchingArgument(
                    connectorContext,
                    fieldArgumentComponentContext
                )
                .successIfDefined {
                    ServiceError.of(
                        """corresponding feature_field_coordinates expected for 
                        |[ field_argument: { path: %s, argument.name: %s } ]"""
                            .flatten(),
                        fieldArgumentComponentContext.path,
                        fieldArgumentComponentContext.argument.name
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

        // Can start at gqlo:/features/__here__ since the feature element type path must have been
        // traversed to reach this feature_field_argument wiring point
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
                            "feature_field_argument_parent calculated [ {} ] for feature_path: [ {} ]",
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
                                            .selectedFieldComponentContextBuilder()
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
                                    .map { sfcc: SelectedFieldComponentContext ->
                                        connectSelectedField(sq, sfcc)
                                    }
                                    .orElseThrow()
                            }
                        }
                    }
            }
            .map { ctx: StandardQuery ->
                // Connect feature field itself
                val featureFieldComponentContext: SelectedFieldComponentContext =
                    ctx.queryComponentContextFactory
                        .selectedFieldComponentContextBuilder()
                        .field(Field.newField().name(fsfc.featureName).build())
                        .path(fsfc.featurePath)
                        .fieldCoordinates(fsfc.featureFieldCoordinates)
                        .canonicalPath(fsfc.featurePath)
                        .build()
                connectSelectedField(ctx, featureFieldComponentContext)
            }
            .map { ctx: StandardQuery ->
                // Connect feature field's arguments
                fsfc.argumentsByPath
                    .asSequence()
                    .map { (p: GQLOperationPath, a: GraphQLArgument) ->
                        connectorContext.queryComponentContextFactory
                            .fieldArgumentComponentContextBuilder()
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
                    .fold(ctx) { sq: StandardQuery, facc: FieldArgumentComponentContext ->
                        connectSelectedFeatureArgument(sq, facc)
                    }
            }
            .map { ctx: StandardQuery ->
                // Connect feature field to this feature argument
                ctx.update {
                    requestGraph(
                        ctx.requestGraph
                            .put(fieldArgumentComponentContext.path, fieldArgumentComponentContext)
                            .putEdge(
                                fieldArgumentComponentContext.path,
                                fsfc.featurePath,
                                MaterializationEdge.EXTRACT_FROM_SOURCE
                            )
                    )
                    putConnectedPathForCanonicalPath(
                        fieldArgumentComponentContext.canonicalPath,
                        fieldArgumentComponentContext.path
                    )
                }
            }
            .orElseThrow()
    }

    private fun getFirstCoordinatesToConnectedDataElementSourcePathsPairOfUnconnectedDataElementFieldMatchingArgument(
        connectorContext: StandardQuery,
        fieldArgumentComponentContext: FieldArgumentComponentContext
    ): Option<Pair<FieldCoordinates, List<GQLOperationPath>>> {
        return connectorContext.materializationMetamodel.dataElementFieldCoordinatesByFieldName
            .getOrNone(fieldArgumentComponentContext.argument.name)
            .filter(ImmutableSet<FieldCoordinates>::isNotEmpty)
            .orElse {
                connectorContext.materializationMetamodel.aliasCoordinatesRegistry
                    .getFieldsWithAlias(fieldArgumentComponentContext.argument.name)
                    .toOption()
                    .filter(ImmutableSet<FieldCoordinates>::isNotEmpty)
            }
            .fold(::emptySequence, ImmutableSet<FieldCoordinates>::asSequence)
            .flatMap { fc: FieldCoordinates ->
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
        fieldArgumentComponentContext: FieldArgumentComponentContext
    ): StandardQuery {
        val (fc: FieldCoordinates, dsdesps: List<GQLOperationPath>) =
            getFirstCoordinatesToConnectedDataElementSourcePathsPairOfUnconnectedDataElementFieldMatchingArgument(
                    connectorContext,
                    fieldArgumentComponentContext
                )
                .successIfDefined {
                    ServiceError.of(
                        """first coordinates to connected data element source paths pair 
                        |expected but not found for matching argument [ path: %s ]"""
                            .flatten(),
                        fieldArgumentComponentContext.path
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
            .fold(connectorContext) {
                ctx: StandardQuery,
                (canonicalDomainPath: GQLOperationPath, domainPath: GQLOperationPath) ->
                val childCanonicalPath: GQLOperationPath =
                    ctx.materializationMetamodel.firstPathWithFieldCoordinatesUnderPath
                        .invoke(fc, canonicalDomainPath)
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
                                canonicalDomainPath
                            )
                        }
                        .orElseThrow()
                val childPath: GQLOperationPath =
                    domainPath.transform {
                        appendSelections(
                            childCanonicalPath.selection.subList(
                                domainPath.selection.size - 1,
                                childCanonicalPath.selection.size
                            )
                        )
                    }
                Try.success(ctx)
                    .map { sq: StandardQuery ->
                        ((canonicalDomainPath.selection.size + 1) until
                                childCanonicalPath.selection.size)
                            .asSequence()
                            .map { limit: Int ->
                                GQLOperationPath.of {
                                    selections(childCanonicalPath.selection.subList(0, limit))
                                } to
                                    GQLOperationPath.of {
                                        selections(childPath.selection.subList(0, limit))
                                    }
                            }
                            .fold(sq) {
                                sq1: StandardQuery,
                                (cp: GQLOperationPath, p: GQLOperationPath) ->
                                logger.debug("cp: {}, p: {}", cp, p)
                                when {
                                    p in sq1.canonicalPathByConnectedPath -> {
                                        sq1
                                    }
                                    else -> {
                                        sq1.materializationMetamodel.fieldCoordinatesByPath
                                            .getOrNone(cp)
                                            .flatMap(ImmutableSet<FieldCoordinates>::firstOrNone)
                                            .map { fc1: FieldCoordinates ->
                                                sq1.queryComponentContextFactory
                                                    .selectedFieldComponentContextBuilder()
                                                    .field(
                                                        Field.newField().name(fc1.fieldName).build()
                                                    )
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
                                            .map { sfcc: SelectedFieldComponentContext ->
                                                connectSelectedField(sq1, sfcc)
                                            }
                                            .orElseThrow()
                                    }
                                }
                            }
                    }
                    .map { sq: StandardQuery ->
                        val childDataElementFieldComponentContext: SelectedFieldComponentContext =
                            sq.queryComponentContextFactory
                                .selectedFieldComponentContextBuilder()
                                .fieldCoordinates(fc)
                                .path(childPath)
                                .canonicalPath(childCanonicalPath)
                                .field(Field.newField().name(fc.fieldName).build())
                                .build()
                        connectSelectedField(sq, childDataElementFieldComponentContext)
                    }
                    .map { sq: StandardQuery ->
                        sq.update {
                            requestGraph(
                                    sq.requestGraph
                                        .put(
                                            fieldArgumentComponentContext.path,
                                            fieldArgumentComponentContext
                                        )
                                        .putEdge(
                                            fieldArgumentComponentContext.path,
                                            childCanonicalPath,
                                            MaterializationEdge.EXTRACT_FROM_SOURCE
                                        )
                                )
                                .putConnectedPathForCanonicalPath(
                                    fieldArgumentComponentContext.canonicalPath,
                                    fieldArgumentComponentContext.path
                                )
                        }
                    }
                    .orElseThrow()
            }
    }

    private fun getFirstCoordinatesToUnconnectedRawInputDataElementSourcePathPairsOfUnconnectedDataElementFieldMatchingArgument(
        connectorContext: StandardQuery,
        fieldArgumentComponentContext: FieldArgumentComponentContext
    ): Option<Pair<FieldCoordinates, List<GQLOperationPath>>> {
        return connectorContext.materializationMetamodel.dataElementFieldCoordinatesByFieldName
            .getOrNone(fieldArgumentComponentContext.argument.name)
            .filter(ImmutableSet<FieldCoordinates>::isNotEmpty)
            .orElse {
                connectorContext.materializationMetamodel.aliasCoordinatesRegistry
                    .getFieldsWithAlias(fieldArgumentComponentContext.argument.name)
                    .toOption()
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
        fieldArgumentComponentContext: FieldArgumentComponentContext
    ): StandardQuery {
        val (fc: FieldCoordinates, dsdesps: List<GQLOperationPath>) =
            getFirstCoordinatesToUnconnectedRawInputDataElementSourcePathPairsOfUnconnectedDataElementFieldMatchingArgument(
                    connectorContext,
                    fieldArgumentComponentContext
                )
                .successIfDefined {
                    ServiceError.of(
                        """first coordinates to unconnected raw input data element source paths pair 
                        |expected but not found for matching argument [ path: %s ]"""
                            .flatten(),
                        fieldArgumentComponentContext.path
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
                                    SelectedFieldComponentContext =
                                    sq.queryComponentContextFactory
                                        .selectedFieldComponentContextBuilder()
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
                                connectSelectedField(
                                    sq,
                                    dataElementElementTypeFieldComponentContext
                                )
                            }
                            else -> {
                                sq
                            }
                        }
                    }
                    .map { sq: StandardQuery ->
                        val domainDataElementFieldContext: SelectedFieldComponentContext =
                            sq.queryComponentContextFactory
                                .selectedFieldComponentContextBuilder()
                                .path(dsdes.domainPath)
                                .field(
                                    Field.newField()
                                        .name(dsdes.domainFieldCoordinates.fieldName)
                                        .build()
                                )
                                .canonicalPath(dsdes.domainPath)
                                .fieldCoordinates(dsdes.domainFieldCoordinates)
                                .build()
                        connectSelectedField(sq, domainDataElementFieldContext)
                    }
                    .map { sq: StandardQuery ->
                        dsdes.argumentsByPath.asSequence().fold(sq) {
                            sq1: StandardQuery,
                            (p: GQLOperationPath, a: GraphQLArgument) ->
                            val facc: FieldArgumentComponentContext =
                                sq1.queryComponentContextFactory
                                    .fieldArgumentComponentContextBuilder()
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
                            connectFieldArgument(sq1, facc)
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
                                                    .selectedFieldComponentContextBuilder()
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
                                            .map { sfcc: SelectedFieldComponentContext ->
                                                connectSelectedField(sq1, sfcc)
                                            }
                                            .orElseThrow()
                                    }
                                }
                            }
                    }
                    .map { sq: StandardQuery ->
                        val childDataElementFieldComponentContext: SelectedFieldComponentContext =
                            sq.queryComponentContextFactory
                                .selectedFieldComponentContextBuilder()
                                .fieldCoordinates(fc)
                                .path(childPath)
                                .canonicalPath(childPath)
                                .field(Field.newField().name(fc.fieldName).build())
                                .build()
                        connectSelectedField(sq, childDataElementFieldComponentContext)
                    }
                    .map { sq: StandardQuery ->
                        sq.update {
                            requestGraph(
                                    sq.requestGraph
                                        .put(
                                            fieldArgumentComponentContext.path,
                                            fieldArgumentComponentContext
                                        )
                                        .putEdge(
                                            fieldArgumentComponentContext.path,
                                            childPath,
                                            MaterializationEdge.EXTRACT_FROM_SOURCE
                                        )
                                )
                                .putConnectedPathForCanonicalPath(
                                    fieldArgumentComponentContext.canonicalPath,
                                    fieldArgumentComponentContext.path
                                )
                        }
                    }
                    .orElseThrow()
            }
    }

    private fun connectSelectedTransformerArgument(
        connectorContext: StandardQuery,
        fieldArgumentComponentContext: FieldArgumentComponentContext
    ): StandardQuery {
        logger.debug(
            "connect_selected_transformer_argument: [ field_argument_component_context.path: {} ]",
            fieldArgumentComponentContext.path
        )
        return connectorContext.update(
            connectTransformerFieldToTransformerArgument(
                connectorContext,
                fieldArgumentComponentContext
            )
        )
    }

    private fun connectTransformerFieldToTransformerArgument(
        connectorContext: StandardQuery,
        fieldArgumentComponentContext: FieldArgumentComponentContext,
    ): (StandardQuery.Builder) -> StandardQuery.Builder {
        return { sqb: StandardQuery.Builder ->
            val fp: GQLOperationPath =
                fieldArgumentComponentContext.path
                    .getParentPath()
                    .filter { p: GQLOperationPath ->
                        connectorContext.requestGraph
                            .get(p)
                            .toOption()
                            .filterIsInstance<SelectedFieldComponentContext>()
                            .filter { sfcc: SelectedFieldComponentContext ->
                                sfcc.fieldCoordinates ==
                                    fieldArgumentComponentContext.fieldCoordinates
                            }
                            .isDefined()
                    }
                    .successIfDefined {
                        ServiceError.of(
                            """transformer_field [ path: %s ] corresponding 
                            |to transformer_argument [ path: %s ] 
                            |not found in request_graph"""
                                .flatten(),
                            fieldArgumentComponentContext.path.getParentPath().orNull(),
                            fieldArgumentComponentContext.path
                        )
                    }
                    .orElseThrow()
            val e: MaterializationEdge =
                when {
                    fieldArgumentComponentContext.argument.value
                        .toOption()
                        .filterIsInstance<VariableReference>()
                        .filter { vr: VariableReference ->
                            vr.name in connectorContext.variableKeys
                        }
                        .isDefined() -> {
                        MaterializationEdge.VARIABLE_VALUE_PROVIDED
                    }
                    fieldArgumentComponentContext.argument.value
                        .toOption()
                        .filter { v: Value<*> -> v !is NullValue }
                        .isDefined() -> {
                        MaterializationEdge.DIRECT_ARGUMENT_VALUE_PROVIDED
                    }
                    connectorContext.materializationMetamodel
                        .transformerSpecifiedTransformerSourcesByCoordinates
                        .getOrNone(fieldArgumentComponentContext.fieldCoordinates)
                        .map(TransformerSpecifiedTransformerSource::argumentsByName)
                        .flatMap { aByName: ImmutableMap<String, GraphQLArgument> ->
                            aByName.getOrNone(fieldArgumentComponentContext.argument.name)
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
                            fieldArgumentComponentContext.path.getParentPath().orNull(),
                            fieldArgumentComponentContext.path
                        )
                    }
                }
            sqb.requestGraph(
                connectorContext.requestGraph
                    .put(fieldArgumentComponentContext.path, fieldArgumentComponentContext)
                    .putEdge(fp, fieldArgumentComponentContext.path, e)
            )
        }
    }

    override fun connectSelectedField(
        connectorContext: StandardQuery,
        selectedFieldComponentContext: SelectedFieldComponentContext
    ): StandardQuery {
        logger.debug(
            "connect_selected_field: [ selected_field_component_context { path: {}, coordinates: {}, canonical_path: {} } ]",
            selectedFieldComponentContext.path,
            selectedFieldComponentContext.fieldCoordinates,
            selectedFieldComponentContext.canonicalPath
        )
        return when {
            selectedFieldComponentContext.fieldCoordinates in
                connectorContext.materializationMetamodel.elementTypeCoordinates -> {
                connectSelectedElementTypeField(connectorContext, selectedFieldComponentContext)
            }
            connectorContext.materializationMetamodel.dataElementElementTypePath.isAncestorTo(
                selectedFieldComponentContext.canonicalPath
            ) -> {
                connectSelectedDataElementField(connectorContext, selectedFieldComponentContext)
            }
            connectorContext.materializationMetamodel.featureElementTypePath.isAncestorTo(
                selectedFieldComponentContext.canonicalPath
            ) -> {
                connectSelectedFeatureField(connectorContext, selectedFieldComponentContext)
            }
            connectorContext.materializationMetamodel.transformerElementTypePath.isAncestorTo(
                selectedFieldComponentContext.canonicalPath
            ) -> {
                connectSelectedTransformerField(connectorContext, selectedFieldComponentContext)
            }
            else -> {
                throw ServiceError.of(
                    "unable to identify element_type bucket for selected_field [ path: %s ]",
                    selectedFieldComponentContext.path
                )
            }
        }
    }

    private fun connectSelectedElementTypeField(
        connectorContext: StandardQuery,
        selectedFieldComponentContext: SelectedFieldComponentContext,
    ): StandardQuery {
        logger.debug(
            "connect_selected_element_type_field: [ selected_field_component_context.path: {} ]",
            selectedFieldComponentContext.path
        )
        return when {
            selectedFieldComponentContext.fieldCoordinates ==
                connectorContext.materializationMetamodel.featureEngineeringModel
                    .dataElementFieldCoordinates &&
                selectedFieldComponentContext.path !in connectorContext.requestGraph -> {
                // Case 1: Connect data_element_element_type_path to root if not already connected
                connectorContext.update {
                    putConnectedFieldPathForCoordinates(
                        selectedFieldComponentContext.fieldCoordinates,
                        selectedFieldComponentContext.path
                    )
                    putConnectedPathForCanonicalPath(
                        selectedFieldComponentContext.canonicalPath,
                        selectedFieldComponentContext.path
                    )
                    requestGraph(
                        connectorContext.requestGraph.put(
                            selectedFieldComponentContext.path,
                            selectedFieldComponentContext
                        )
                    )
                }
            }
            else -> {
                connectorContext.update {
                    putConnectedFieldPathForCoordinates(
                        selectedFieldComponentContext.fieldCoordinates,
                        selectedFieldComponentContext.path
                    )
                    putConnectedPathForCanonicalPath(
                        selectedFieldComponentContext.canonicalPath,
                        selectedFieldComponentContext.path
                    )
                }
            }
        }
    }

    private fun connectSelectedDataElementField(
        connectorContext: StandardQuery,
        selectedFieldComponentContext: SelectedFieldComponentContext,
    ): StandardQuery {
        logger.debug(
            "connect_selected_data_element_field: [ selected_field_component_context.path: {} ]",
            selectedFieldComponentContext.path
        )
        return when {
            selectedFieldComponentContext.fieldCoordinates in
                connectorContext.materializationMetamodel
                    .domainSpecifiedDataElementSourceByCoordinates -> {
                connectorContext.update(
                    connectDomainDataElementField(connectorContext, selectedFieldComponentContext)
                )
            }
            else -> {
                connectSubdomainDataElementField(connectorContext, selectedFieldComponentContext)
            }
        }
    }

    private fun connectDomainDataElementField(
        connectorContext: StandardQuery,
        selectedFieldComponentContext: SelectedFieldComponentContext
    ): (StandardQuery.Builder) -> StandardQuery.Builder {
        return { sqb: StandardQuery.Builder ->
            val decb: DataElementCallable.Builder =
                connectorContext.materializationMetamodel
                    .domainSpecifiedDataElementSourceByCoordinates
                    .getOrNone(selectedFieldComponentContext.fieldCoordinates)
                    .successIfDefined {
                        ServiceError.of(
                            "%s not found at [ coordinates: %s ]",
                            DomainSpecifiedDataElementSource::class.simpleName,
                            selectedFieldComponentContext.fieldCoordinates
                        )
                    }
                    .map { dsdes: DomainSpecifiedDataElementSource ->
                        dsdes.dataElementSource.builder().selectDomain(dsdes)
                    }
                    .orElseThrow()
            sqb.requestGraph(
                    connectorContext.requestGraph.put(
                        selectedFieldComponentContext.path,
                        selectedFieldComponentContext
                    )
                )
                .putDataElementCallableBuilderForPath(selectedFieldComponentContext.path, decb)
                .putConnectedFieldPathForCoordinates(
                    selectedFieldComponentContext.fieldCoordinates,
                    selectedFieldComponentContext.path
                )
                .putConnectedPathForCanonicalPath(
                    selectedFieldComponentContext.canonicalPath,
                    selectedFieldComponentContext.path
                )
        }
    }

    private fun connectSubdomainDataElementField(
        connectorContext: StandardQuery,
        selectedFieldComponentContext: SelectedFieldComponentContext,
    ): StandardQuery {
        logger.debug(
            "connect_subdomain_data_element_field: [ selected_field_component_context.path: {} ]",
            selectedFieldComponentContext.path
        )
        return Try.success(connectorContext)
            .map { sq: StandardQuery ->
                connectLastUpdatedDataElementFieldRelatedToSubdomainDataElementField(
                    sq,
                    selectedFieldComponentContext
                )
            }
            .map { sq: StandardQuery ->
                sq.update(
                    connectSelectedDataElementFieldToDomainDataElementField(
                        sq,
                        selectedFieldComponentContext
                    )
                )
            }
            .orElseThrow()
    }

    private fun connectLastUpdatedDataElementFieldRelatedToSubdomainDataElementField(
        connectorContext: StandardQuery,
        selectedFieldComponentContext: SelectedFieldComponentContext
    ): StandardQuery {
        return selectedFieldComponentContext.path
            .getParentPath()
            .flatMap { selectedFieldParentPath: GQLOperationPath ->
                if (selectedFieldParentPath in connectorContext.dataElementCallableBuildersByPath) {
                    connectorContext.requestGraph
                        .get(selectedFieldParentPath)
                        .toOption()
                        .filterIsInstance<SelectedFieldComponentContext>()
                } else {
                    connectorContext.requestGraph
                        .successorVertices(selectedFieldParentPath)
                        .firstOrNone()
                        .map { (_: GQLOperationPath, qcc: QueryComponentContext) -> qcc }
                        .filterIsInstance<SelectedFieldComponentContext>()
                }
            }
            .filter { lastUpdatedFieldDomainContext: SelectedFieldComponentContext ->
                lastUpdatedFieldDomainContext.fieldCoordinates in
                    connectorContext.materializationMetamodel
                        .domainSpecifiedDataElementSourceByCoordinates
            }
            .flatMap { lastUpdatedFieldDomainContext: SelectedFieldComponentContext ->
                connectorContext.materializationMetamodel
                    .domainSpecifiedDataElementSourceByCoordinates
                    .getOrNone(lastUpdatedFieldDomainContext.fieldCoordinates)
                    .flatMap { dsdes: DomainSpecifiedDataElementSource ->
                        dsdes.lastUpdatedCoordinatesRegistry
                            .findNearestLastUpdatedField(
                                selectedFieldComponentContext.canonicalPath
                            )
                            .filter { (_: GQLOperationPath, fcs: Set<FieldCoordinates>) ->
                                selectedFieldComponentContext.fieldCoordinates !in fcs
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
                                    .selectedFieldComponentContextBuilder()
                                    .field(Field.newField().name(fc.fieldName).build())
                                    .fieldCoordinates(fc)
                                    .path(p)
                                    .canonicalPath(cp)
                                    .build()
                            }
                    }
            }
            .map { lastUpdatedFieldContext: SelectedFieldComponentContext ->
                when {
                    !connectorContext.requestGraph.contains(lastUpdatedFieldContext.path) -> {
                        connectSelectedField(
                            connectorContext.update {
                                putLastUpdatedDataElementPathForDataElementPath(
                                    selectedFieldComponentContext.path,
                                    lastUpdatedFieldContext.path
                                )
                            },
                            lastUpdatedFieldContext
                        )
                    }
                    else -> {
                        connectorContext.update {
                            putLastUpdatedDataElementPathForDataElementPath(
                                selectedFieldComponentContext.path,
                                lastUpdatedFieldContext.path
                            )
                        }
                    }
                }
            }
            .getOrElse { connectorContext }
    }

    private fun connectSelectedDataElementFieldToDomainDataElementField(
        connectorContext: StandardQuery,
        selectedFieldComponentContext: SelectedFieldComponentContext,
    ): (StandardQuery.Builder) -> StandardQuery.Builder {
        return { sqb: StandardQuery.Builder ->
            val parentPath: GQLOperationPath =
                selectedFieldComponentContext.path
                    .getParentPath()
                    .successIfDefined {
                        ServiceError.of(
                            "the selected_data_element should be processed by an element_type method [ field.name: %s ]",
                            selectedFieldComponentContext.field.name
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
            if (parentPath in connectorContext.dataElementCallableBuildersByPath) {
                sqb.requestGraph(
                        connectorContext.requestGraph
                            .put(selectedFieldComponentContext.path, selectedFieldComponentContext)
                            .putEdge(
                                selectedFieldComponentContext.path,
                                parentPath,
                                MaterializationEdge.EXTRACT_FROM_SOURCE
                            )
                    )
                    .putDataElementCallableBuilderForPath(
                        parentPath,
                        connectorContext.dataElementCallableBuildersByPath
                            .get(parentPath)!!
                            .selectPathWithinDomain(selectedFieldComponentContext.path)
                    )
                    .putConnectedFieldPathForCoordinates(
                        selectedFieldComponentContext.fieldCoordinates,
                        selectedFieldComponentContext.path
                    )
                    .putConnectedPathForCanonicalPath(
                        selectedFieldComponentContext.canonicalPath,
                        selectedFieldComponentContext.path
                    )
            } else {
                val (domainPath: GQLOperationPath, _: QueryComponentContext) =
                    connectorContext.requestGraph.successorVertices(parentPath).first()
                if (domainPath !in connectorContext.dataElementCallableBuildersByPath) {
                    throw ServiceError.of(
                        "domain_data_element_callable has not been created for [ path: %s ]",
                        domainPath
                    )
                }
                sqb.requestGraph(
                        connectorContext.requestGraph
                            .put(selectedFieldComponentContext.path, selectedFieldComponentContext)
                            .putEdge(
                                selectedFieldComponentContext.path,
                                domainPath,
                                MaterializationEdge.EXTRACT_FROM_SOURCE
                            )
                    )
                    .putDataElementCallableBuilderForPath(
                        domainPath,
                        connectorContext.dataElementCallableBuildersByPath[domainPath]!!
                            .selectPathWithinDomain(selectedFieldComponentContext.path)
                    )
                    .putConnectedFieldPathForCoordinates(
                        selectedFieldComponentContext.fieldCoordinates,
                        selectedFieldComponentContext.path
                    )
                    .putConnectedPathForCanonicalPath(
                        selectedFieldComponentContext.canonicalPath,
                        selectedFieldComponentContext.path
                    )
            }
        }
    }

    private fun connectSelectedTransformerField(
        connectorContext: StandardQuery,
        selectedFieldComponentContext: SelectedFieldComponentContext,
    ): StandardQuery {
        logger.debug(
            "connect_selected_transformer_field: [ selected_field_component_context.path: {} ]",
            selectedFieldComponentContext.path
        )
        return when {
            selectedFieldComponentContext.fieldCoordinates in
                connectorContext.materializationMetamodel
                    .transformerSpecifiedTransformerSourcesByCoordinates -> {
                connectorContext.update(
                    connectTransformerForSelectedTransformerField(
                        connectorContext,
                        selectedFieldComponentContext
                    )
                )
            }
            else -> {
                // paths belonging to object types do not need to be wired
                connectorContext
            }
        }
    }

    private fun connectTransformerForSelectedTransformerField(
        connectorContext: StandardQuery,
        selectedFieldComponentContext: SelectedFieldComponentContext
    ): (StandardQuery.Builder) -> StandardQuery.Builder {
        return { sqb: StandardQuery.Builder ->
            val tsts: TransformerSpecifiedTransformerSource =
                connectorContext.materializationMetamodel
                    .transformerSpecifiedTransformerSourcesByCoordinates
                    .getOrNone(selectedFieldComponentContext.fieldCoordinates)
                    .successIfDefined {
                        ServiceError.of(
                            "%s not found at [ coordinates: %s ]",
                            TransformerSpecifiedTransformerSource::class.simpleName,
                            selectedFieldComponentContext.fieldCoordinates
                        )
                    }
                    .orElseThrow()
            sqb.requestGraph(
                    connectorContext.requestGraph.put(
                        selectedFieldComponentContext.path,
                        selectedFieldComponentContext
                    )
                )
                .putTransformerCallableForPath(
                    selectedFieldComponentContext.path,
                    tsts.transformerSource
                        .builder()
                        .selectTransformer(
                            tsts.transformerFieldCoordinates,
                            tsts.transformerPath,
                            tsts.transformerFieldDefinition
                        )
                        .build()
                )
                .putConnectedFieldPathForCoordinates(
                    selectedFieldComponentContext.fieldCoordinates,
                    selectedFieldComponentContext.path
                )
                .putConnectedPathForCanonicalPath(
                    selectedFieldComponentContext.canonicalPath,
                    selectedFieldComponentContext.path
                )
        }
    }

    private fun connectSelectedFeatureField(
        connectorContext: StandardQuery,
        selectedFieldComponentContext: SelectedFieldComponentContext,
    ): StandardQuery {
        logger.debug(
            "connect_selected_feature_field: [ selected_field_component_context.path: {} ]",
            selectedFieldComponentContext.path
        )
        return when {
            selectedFieldComponentContext.path in
                connectorContext.featureCalculatorCallablesByPath -> {
                connectorContext
            }
            selectedFieldComponentContext.fieldCoordinates in
                connectorContext.materializationMetamodel
                    .featureSpecifiedFeatureCalculatorsByCoordinates -> {
                connectorContext.update(
                    connectFeatureCalculatorForSelectedFeatureField(
                        connectorContext,
                        selectedFieldComponentContext
                    )
                )
            }
            extractGraphQLFieldDefinitionForCoordinates(
                    connectorContext,
                    selectedFieldComponentContext.fieldCoordinates
                )
                .mapNotNull(GraphQLFieldDefinition::getType)
                .mapNotNull(GraphQLTypeUtil::unwrapAll)
                .filterIsInstance<GraphQLCompositeType>()
                .isDefined() -> {
                // feature object type containers do not require wiring
                connectorContext.update {
                    putConnectedFieldPathForCoordinates(
                        selectedFieldComponentContext.fieldCoordinates,
                        selectedFieldComponentContext.path
                    )
                    putConnectedPathForCanonicalPath(
                        selectedFieldComponentContext.canonicalPath,
                        selectedFieldComponentContext.path
                    )
                }
            }
            else -> {
                throw ServiceError.of(
                    "unhandled selected_feature_field [ path: %s ]",
                    selectedFieldComponentContext.path
                )
            }
        }
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

    private fun connectFeatureCalculatorForSelectedFeatureField(
        connectorContext: StandardQuery,
        selectedFieldComponentContext: SelectedFieldComponentContext,
    ): (StandardQuery.Builder) -> StandardQuery.Builder {
        return { sqb: StandardQuery.Builder ->
            val fsfc: FeatureSpecifiedFeatureCalculator =
                connectorContext.materializationMetamodel
                    .featureSpecifiedFeatureCalculatorsByCoordinates
                    .getOrNone(selectedFieldComponentContext.fieldCoordinates)
                    .successIfDefined {
                        ServiceError.of(
                            "%s not found at [ coordinates: %s ]",
                            FeatureSpecifiedFeatureCalculator::class.simpleName,
                            selectedFieldComponentContext.fieldCoordinates
                        )
                    }
                    .orElseThrow()
            val fcb: FeatureCalculatorCallable.Builder =
                fsfc.featureCalculator
                    .builder()
                    .selectFeature(
                        fsfc.featureFieldCoordinates,
                        fsfc.featurePath,
                        fsfc.featureFieldDefinition
                    )
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
                        tsts.transformerSource
                            .builder()
                            .selectTransformer(
                                tsts.transformerFieldCoordinates,
                                tsts.transformerPath,
                                tsts.transformerFieldDefinition
                            )
                            .build()
                    }
                    .orElseThrow()
            sqb.requestGraph(
                    connectorContext.requestGraph.put(
                        selectedFieldComponentContext.path,
                        selectedFieldComponentContext
                    )
                )
                .putFeatureCalculatorCallableForPath(
                    selectedFieldComponentContext.path,
                    fcb.setTransformerCallable(tc).build()
                )
                .putConnectedFieldPathForCoordinates(
                    selectedFieldComponentContext.fieldCoordinates,
                    selectedFieldComponentContext.path
                )
                .putConnectedPathForCanonicalPath(
                    selectedFieldComponentContext.canonicalPath,
                    selectedFieldComponentContext.path
                )
            fsfc.featureCalculator.featureStoreName
                .toOption()
                .filterNot(FeatureCalculator.FEATURE_STORE_NOT_PROVIDED::equals)
                .flatMap { storeName: String ->
                    connectorContext.materializationMetamodel.featureEngineeringModel
                        .featureJsonValueStoresByName
                        .getOrNone(storeName)
                }
                .fold({ sqb }) { fjvs: FeatureJsonValueStore ->
                    sqb.putFeatureJsonValueStoreForPath(selectedFieldComponentContext.path, fjvs)
                }
            fsfc.featureCalculator.featurePublisherName
                .toOption()
                .filterNot(FeatureCalculator.FEATURE_PUBLISHER_NOT_PROVIDED::equals)
                .flatMap { publisherName: String ->
                    connectorContext.materializationMetamodel.featureEngineeringModel
                        .featureJsonValuePublishersByName
                        .getOrNone(publisherName)
                }
                .fold({ sqb }) { fjvp: FeatureJsonValuePublisher ->
                    sqb.putFeatureJsonValuePublisherForPath(
                        selectedFieldComponentContext.path,
                        fjvp
                    )
                }
        }
    }
}
