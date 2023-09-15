package funcify.feature.materializer.graph.connector

import arrow.core.*
import funcify.feature.error.ServiceError
import funcify.feature.graph.DirectedPersistentGraph
import funcify.feature.materializer.graph.MaterializationEdge
import funcify.feature.materializer.graph.component.QueryComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContext.FieldArgumentComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContext.SelectedFieldComponentContext
import funcify.feature.materializer.graph.context.StandardQuery
import funcify.feature.schema.dataelement.DataElementCallable
import funcify.feature.schema.dataelement.DomainSpecifiedDataElementSource
import funcify.feature.schema.feature.FeatureCalculatorCallable
import funcify.feature.schema.feature.FeatureSpecifiedFeatureCalculator
import funcify.feature.schema.path.operation.AliasedFieldSegment
import funcify.feature.schema.path.operation.FieldSegment
import funcify.feature.schema.path.operation.FragmentSpreadSegment
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.path.operation.InlineFragmentSegment
import funcify.feature.schema.path.operation.SelectionSegment
import funcify.feature.schema.transformer.TransformerCallable
import funcify.feature.schema.transformer.TransformerSpecifiedTransformerSource
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.SequenceExtensions.flatMapOptions
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.introspection.Introspection
import graphql.language.Argument
import graphql.language.Field
import graphql.language.Node
import graphql.language.NullValue
import graphql.language.Value
import graphql.language.VariableReference
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLCompositeType
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLTypeUtil
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import org.slf4j.Logger

/**
 * @author smccarron
 * @created 2023-08-05
 */
object StandardQueryConnector : RequestMaterializationGraphConnector<StandardQuery> {

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
                TODO("subdomain domain element arguments not yet handled")
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
                    fieldArgumentComponentContext.argument.value
                        .toOption()
                        .filterIsInstance<VariableReference>()
                        .mapNotNull(VariableReference::getName)
                        .filter(connectorContext.variableKeys::contains)
                        .isDefined() -> {
                        MaterializationEdge.ARGUMENT_VALUE_PROVIDED
                    }
                    connectorContext.rawInputContextKeys.contains(a.name) ||
                        connectorContext.materializationMetamodel.aliasCoordinatesRegistry
                            .getAllAliasesForFieldArgument(faLoc)
                            .any { n: String ->
                                connectorContext.rawInputContextKeys.contains(n)
                            } -> {
                        MaterializationEdge.RAW_INPUT_VALUE_PROVIDED
                    }
                    connectorContext.requestGraph[fieldPath]
                        .toOption()
                        .filterIsInstance<SelectedFieldComponentContext>()
                        .map(SelectedFieldComponentContext::field)
                        .flatMap { n: Node<*> ->
                            n.toOption().filterIsInstance<Field>().map(Field::getName).orElse {
                                fieldPath.selection.lastOrNone().map { ss: SelectionSegment ->
                                    when (ss) {
                                        is FieldSegment -> ss.fieldName
                                        is AliasedFieldSegment -> ss.fieldName
                                        is FragmentSpreadSegment -> ss.selectedField.fieldName
                                        is InlineFragmentSegment -> ss.selectedField.fieldName
                                    }
                                }
                            }
                        }
                        .filter(connectorContext.rawInputContextKeys::contains)
                        .isDefined() -> {
                        MaterializationEdge.RAW_INPUT_VALUE_PROVIDED
                    }
                    a.value
                        .toOption()
                        .filterNot { v: Value<*> -> v !is VariableReference }
                        .and(
                            connectorContext.materializationMetamodel
                                .domainSpecifiedDataElementSourceByPath
                                .getOrNone(fieldPath)
                                .map(
                                    DomainSpecifiedDataElementSource::
                                        argumentsWithDefaultValuesByName
                                )
                                .map { m: ImmutableMap<String, GraphQLArgument> ->
                                    m.containsKey(a.name) ||
                                        connectorContext.materializationMetamodel
                                            .aliasCoordinatesRegistry
                                            .getAllAliasesForFieldArgument(faLoc)
                                            .any { n: String -> m.containsKey(n) }
                                }
                        )
                        .getOrElse { false } -> {
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
                TODO("feature argument case not yet handled")
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
                            MaterializationEdge.ARGUMENT_VALUE_PROVIDED
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
            getFieldCoordinatesOfConnectedDataElementOrFeatureFieldCorrespondingToFieldArgument(
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
            getFieldCoordinatesOfUnconnectedFeatureFieldCorrespondingToFieldArgument(
                    connectorContext,
                    fieldArgumentComponentContext
                )
                .isDefined() -> {
                connectFeatureFieldArgumentToUnconnectedFeatureField(
                    connectorContext,
                    fieldArgumentComponentContext
                )
            }
            else -> {
                TODO("unhandled feature_field to feature_argument to data_element_field case")
            }
        }
    }

    private fun getFieldCoordinatesOfConnectedDataElementOrFeatureFieldCorrespondingToFieldArgument(
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
                getFieldCoordinatesOfConnectedDataElementOrFeatureFieldCorrespondingToFieldArgument(
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

    private fun getFieldCoordinatesOfUnconnectedFeatureFieldCorrespondingToFieldArgument(
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
        TODO("Not yet implemented")
    }

    private fun determineVerticesToAddInOrderToConnectFeatureArgument(
        connectorContext: StandardQuery,
        fieldArgumentComponentContext: FieldArgumentComponentContext
    ): StandardQuery {
        return when {
            // Case 1: argument.name points to another feature, either directly or through alias
            connectorContext.materializationMetamodel.featureCoordinatesByName
                .getOrNone(fieldArgumentComponentContext.argument.name)
                .orElse {
                    connectorContext.materializationMetamodel.aliasCoordinatesRegistry
                        .getFieldsWithAlias(fieldArgumentComponentContext.argument.name)
                        .firstOrNone(
                            connectorContext.materializationMetamodel
                                .featureSpecifiedFeatureCalculatorsByCoordinates::containsKey
                        )
                }
                .isDefined() -> {
                connectorContext.materializationMetamodel.featureCoordinatesByName
                    .getOrNone(fieldArgumentComponentContext.argument.name)
                    .orElse {
                        connectorContext.materializationMetamodel.aliasCoordinatesRegistry
                            .getFieldsWithAlias(fieldArgumentComponentContext.argument.name)
                            .firstOrNone(
                                connectorContext.materializationMetamodel
                                    .featureSpecifiedFeatureCalculatorsByCoordinates::containsKey
                            )
                    }
                    .flatMap(
                        connectorContext.materializationMetamodel
                            .featureSpecifiedFeatureCalculatorsByCoordinates::getOrNone
                    )
                    .map { fsfc: FeatureSpecifiedFeatureCalculator ->
                        val featureParentFieldComponentContexts:
                            List<SelectedFieldComponentContext> =
                            ((connectorContext.materializationMetamodel.featureElementTypePath
                                    .selection
                                    .size + 1) until fsfc.featurePath.selection.size)
                                .asSequence()
                                .map { limit: Int ->
                                    GQLOperationPath.of {
                                        selections(fsfc.featurePath.selection.subList(0, limit))
                                    }
                                }
                                .map { p: GQLOperationPath ->
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
                                }
                                .flatMapOptions()
                                .toList()
                        logger.debug(
                            "dependent_feature_parent_paths: {}",
                            featureParentFieldComponentContexts
                                .asSequence()
                                .map(SelectedFieldComponentContext::path)
                                .joinToString(", ")
                        )
                        val argumentComponentContexts: List<FieldArgumentComponentContext> =
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
                                                    a.argumentDefaultValue.value
                                                        .toOption()
                                                        .filterIsInstance<Value<*>>()
                                                        .getOrElse { NullValue.of() }
                                                )
                                                .build()
                                        )
                                        .fieldCoordinates(fsfc.featureFieldCoordinates)
                                        .canonicalPath(p)
                                        .build()
                                }
                                .toList()
                        val featureFieldComponentContext: SelectedFieldComponentContext =
                            connectorContext.queryComponentContextFactory
                                .selectedFieldComponentContextBuilder()
                                .field(Field.newField().name(fsfc.featureName).build())
                                .path(fsfc.featurePath)
                                .fieldCoordinates(fsfc.featureFieldCoordinates)
                                .canonicalPath(fsfc.featurePath)
                                .build()
                        connectorContext.update {
                            requestGraph(
                                connectorContext.requestGraph
                                    .put(
                                        fieldArgumentComponentContext.path,
                                        fieldArgumentComponentContext
                                    )
                                    .put(
                                        featureFieldComponentContext.path,
                                        featureFieldComponentContext
                                    )
                                    .putEdge(
                                        fieldArgumentComponentContext.path,
                                        featureFieldComponentContext.path,
                                        MaterializationEdge.EXTRACT_FROM_SOURCE
                                    )
                            )
                            // TODO: Connect argument and field contexts
                            putConnectedPathForCanonicalPath(
                                fieldArgumentComponentContext.canonicalPath,
                                fieldArgumentComponentContext.path
                            )
                        }
                    }
                    .getOrElse { connectorContext }
            }
            // Case 2: path under already specified domain_data_element_source
            connectorContext.materializationMetamodel.dataElementFieldCoordinatesByFieldName
                .getOrNone(fieldArgumentComponentContext.argument.name)
                .filter(ImmutableSet<FieldCoordinates>::isNotEmpty)
                .orElse {
                    connectorContext.materializationMetamodel.aliasCoordinatesRegistry
                        .getFieldsWithAlias(fieldArgumentComponentContext.argument.name)
                        .toOption()
                        .filter(ImmutableSet<FieldCoordinates>::isNotEmpty)
                }
                .fold(::emptySequence, ImmutableSet<FieldCoordinates>::asSequence)
                .firstOrNone { fc: FieldCoordinates ->
                    connectorContext.dataElementCallableBuildersByPath.keys
                        .asSequence()
                        .firstOrNone { p: GQLOperationPath ->
                            connectorContext.canonicalPathByConnectedPath
                                .getOrNone(p)
                                .filter { cp: GQLOperationPath ->
                                    connectorContext.materializationMetamodel
                                        .fieldCoordinatesAvailableUnderPath
                                        .invoke(fc, cp)
                                }
                                .isDefined()
                        }
                        .isDefined()
                }
                .isDefined() -> {
                connectorContext.materializationMetamodel.dataElementFieldCoordinatesByFieldName
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
                    .map { (fc: FieldCoordinates, ps: List<GQLOperationPath>) ->
                        ps.asSequence()
                            .map { domainDataElementPath: GQLOperationPath ->
                                connectorContext.canonicalPathByConnectedPath
                                    .getOrNone(domainDataElementPath)
                                    .flatMap { canonicalDomainDataElementPath: GQLOperationPath ->
                                        connectorContext.materializationMetamodel
                                            .firstPathWithFieldCoordinatesUnderPath
                                            .invoke(fc, canonicalDomainDataElementPath)
                                            .map { canonicalChildDomainElementPath: GQLOperationPath
                                                ->
                                                domainDataElementPath.transform {
                                                    appendSelections(
                                                        canonicalChildDomainElementPath.selection
                                                            .subList(
                                                                domainDataElementPath.selection
                                                                    .size,
                                                                canonicalChildDomainElementPath
                                                                    .selection
                                                                    .size
                                                            )
                                                    )
                                                } to canonicalChildDomainElementPath
                                            }
                                    }
                                    .map {
                                        (
                                            childDataElementPath: GQLOperationPath,
                                            canonicalChildDataElementPath: GQLOperationPath) ->
                                        connectorContext.queryComponentContextFactory
                                            .selectedFieldComponentContextBuilder()
                                            .path(childDataElementPath)
                                            .canonicalPath(canonicalChildDataElementPath)
                                            .fieldCoordinates(fc)
                                            // TODO: Add handling for subselection data element
                                            // fields
                                            // that take arguments
                                            .field(Field.newField().name(fc.fieldName).build())
                                            .build()
                                    }
                            }
                            .flatMapOptions()
                    }
                    .fold(::emptySequence, ::identity)
                    .fold(connectorContext) { sq: StandardQuery, sfcc: SelectedFieldComponentContext
                        ->
                        // TODO: Handle adding all intermediate missing connections: parents,
                        // grandparents
                        sq.update {
                            requestGraph(
                                sq.requestGraph
                                    .put(
                                        fieldArgumentComponentContext.path,
                                        fieldArgumentComponentContext
                                    )
                                    .put(sfcc.path, sfcc)
                                    .putEdge(
                                        fieldArgumentComponentContext.path,
                                        sfcc.path,
                                        MaterializationEdge.EXTRACT_FROM_SOURCE
                                    )
                            )
                        }
                    }
            }
            // Case 3: path under not yet specified domain_data_element_source but one for which the
            // argument names can be found in the variable keys
            // => tricky because there could be more than one candidate domain_data_element_source
            // that matches this criteria
            // ---ideally the selected domain _covers_ the most features without data_elements
            // selected
            connectorContext.materializationMetamodel.dataElementFieldCoordinatesByFieldName
                .getOrNone(fieldArgumentComponentContext.argument.name)
                .filter(ImmutableSet<FieldCoordinates>::isNotEmpty)
                .orElse {
                    connectorContext.materializationMetamodel.aliasCoordinatesRegistry
                        .getFieldsWithAlias(fieldArgumentComponentContext.argument.name)
                        .toOption()
                        .filter(ImmutableSet<FieldCoordinates>::isNotEmpty)
                }
                .isDefined() -> {
                connectorContext.materializationMetamodel.dataElementFieldCoordinatesByFieldName
                    .getOrNone(fieldArgumentComponentContext.argument.name)
                    .filter(ImmutableSet<FieldCoordinates>::isNotEmpty)
                    .orElse {
                        connectorContext.materializationMetamodel.aliasCoordinatesRegistry
                            .getFieldsWithAlias(fieldArgumentComponentContext.argument.name)
                            .toOption()
                            .filter(ImmutableSet<FieldCoordinates>::isNotEmpty)
                    }
                    .fold(::emptySequence, ImmutableSet<FieldCoordinates>::asSequence)
                    .map { fc: FieldCoordinates ->
                        connectorContext.materializationMetamodel
                            .domainSpecifiedDataElementSourceByPath
                            .asSequence()
                            .filter { (p: GQLOperationPath, _: DomainSpecifiedDataElementSource) ->
                                // exclude those already assessed in prior condition i.e. those
                                // already connected
                                p !in connectorContext.connectedPathsByCanonicalPath &&
                                    connectorContext.materializationMetamodel
                                        .fieldCoordinatesAvailableUnderPath
                                        .invoke(fc, p)
                            }
                    }

                connectorContext
            }
            else -> {
                connectorContext
            }
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
        return connectorContext
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
                connectorContext.update(
                    connectSelectedDataElementFieldToDomainDataElementField(
                        connectorContext,
                        selectedFieldComponentContext
                    )
                )
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
                        dsdes.dataElementSource
                            .builder()
                            .selectDomain(
                                dsdes.domainFieldCoordinates,
                                dsdes.domainPath,
                                dsdes.domainFieldDefinition
                            )
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
            selectedFieldComponentContext.path in connectorContext.transformerCallablesByPath -> {
                connectorContext
            }
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
            sqb.putTransformerCallableForPath(
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
                    fcb.addTransformerCallable(tc).build()
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
