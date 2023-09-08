package funcify.feature.materializer.graph.connector

import arrow.core.None
import arrow.core.Option
import arrow.core.filterIsInstance
import arrow.core.firstOrNone
import arrow.core.getOrElse
import arrow.core.getOrNone
import arrow.core.toOption
import funcify.feature.error.ServiceError
import funcify.feature.materializer.graph.MaterializationEdge
import funcify.feature.materializer.graph.component.QueryComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContext.FieldArgumentComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContext.SelectedFieldComponentContext
import funcify.feature.materializer.graph.context.StandardQuery
import funcify.feature.schema.dataelement.DataElementCallable
import funcify.feature.schema.dataelement.DomainSpecifiedDataElementSource
import funcify.feature.schema.feature.FeatureCalculatorCallable
import funcify.feature.schema.feature.FeatureSpecifiedFeatureCalculator
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.transformer.TransformerCallable
import funcify.feature.schema.transformer.TransformerSpecifiedTransformerSource
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.introspection.Introspection
import graphql.language.Argument
import graphql.language.Field
import graphql.language.Node
import graphql.language.OperationDefinition
import graphql.language.Value
import graphql.language.VariableReference
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLCompositeType
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLTypeUtil
import graphql.schema.SelectedField
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import org.slf4j.Logger

/**
 * @author smccarron
 * @created 2023-08-05
 */
object StandardQueryConnector : RequestMaterializationGraphConnector<StandardQuery> {

    private val logger: Logger = loggerFor<StandardQueryConnector>()

    override fun startOperationDefinition(connectorContext: StandardQuery): StandardQuery {
        logger.debug(
            "connect_operation_definition: [ connector_context.operation_name: {} ]",
            connectorContext.operationName
        )
        return when (
            val od: OperationDefinition? =
                connectorContext.document.definitions
                    .asSequence()
                    .filterIsInstance<OperationDefinition>()
                    .filter { od: OperationDefinition ->
                        if (connectorContext.operationName.isNotBlank()) {
                            od.name == connectorContext.operationName
                        } else {
                            true
                        }
                    }
                    .firstOrNone()
                    .orNull()
        ) {
            /*
             * [graphql.language.Document.getOperationDefinition] does not handle _null_ or empty
             * names as expected
             *
             * => filter for operation_name only if it's not blank, else use the first found
             */
            null -> {
                throw ServiceError.of(
                    "GraphQL document does not contain an operation_definition with [ name: %s ][ actual: %s ]",
                    connectorContext.operationName,
                    connectorContext.document
                )
            }
            else -> {
                connectorContext.update {
                    StandardQueryTraverser(
                            queryComponentContextFactory =
                                connectorContext.queryComponentContextFactory,
                            materializationMetamodel = connectorContext.materializationMetamodel
                        )
                        .invoke(
                            operationName = connectorContext.operationName,
                            document = connectorContext.document
                        )
                        .fold(
                            this.requestGraph(
                                connectorContext.requestGraph.put(
                                    GQLOperationPath.getRootPath(),
                                    od
                                )
                            )
                        ) { sqb: StandardQuery.Builder, qcc: QueryComponentContext ->
                            logger.debug("query_component_context: {}", qcc)
                            sqb.addVertexContext(qcc)
                        }
                }
            }
        }
    }

    override fun connectFieldArgument(
        connectorContext: StandardQuery,
        fieldArgumentComponentContext: FieldArgumentComponentContext,
    ): StandardQuery {
        logger.debug(
            "connect_field_argument: [ field_argument_component_context.path: {} ]",
            fieldArgumentComponentContext.path
        )
        val p: GQLOperationPath = fieldArgumentComponentContext.path.toUnaliasedPath()
        val elementTypeSegmentNotOnFragment: Boolean =
            p.selection.firstOrNone().filterIsInstance<SelectedField>().isDefined()
        val elementTypeSegmentOnFragment: Boolean = !elementTypeSegmentNotOnFragment
        return when {
            fieldArgumentComponentContext.fieldCoordinates in
                connectorContext.materializationMetamodel.elementTypeCoordinates -> {
                throw ServiceError.of("no arguments are permitted on element_type fields")
            }
            elementTypeSegmentNotOnFragment &&
                connectorContext.materializationMetamodel.dataElementElementTypePath.isAncestorTo(
                    p
                ) -> {
                connectSelectedDataElementArgument(connectorContext, fieldArgumentComponentContext)
            }
            elementTypeSegmentNotOnFragment &&
                connectorContext.materializationMetamodel.featureElementTypePath.isAncestorTo(
                    p
                ) -> {
                connectSelectedFeatureArgument(connectorContext, fieldArgumentComponentContext)
            }
            elementTypeSegmentNotOnFragment &&
                connectorContext.materializationMetamodel.transformerElementTypePath.isAncestorTo(
                    p
                ) -> {
                connectSelectedTransformerArgument(connectorContext, fieldArgumentComponentContext)
            }
            elementTypeSegmentOnFragment &&
                connectorContext.materializationMetamodel.pathsByFieldCoordinates
                    .getOrNone(fieldArgumentComponentContext.fieldCoordinates)
                    .flatMap(ImmutableSet<GQLOperationPath>::firstOrNone)
                    .filter { cp: GQLOperationPath ->
                        connectorContext.materializationMetamodel.dataElementElementTypePath
                            .isAncestorTo(cp)
                    }
                    .isDefined() -> {
                connectSelectedDataElementArgument(connectorContext, fieldArgumentComponentContext)
            }
            elementTypeSegmentOnFragment &&
                connectorContext.materializationMetamodel.pathsByFieldCoordinates
                    .getOrNone(fieldArgumentComponentContext.fieldCoordinates)
                    .flatMap(ImmutableSet<GQLOperationPath>::firstOrNone)
                    .filter { cp: GQLOperationPath ->
                        connectorContext.materializationMetamodel.featureElementTypePath
                            .isAncestorTo(cp)
                    }
                    .isDefined() -> {
                connectSelectedFeatureArgument(connectorContext, fieldArgumentComponentContext)
            }
            elementTypeSegmentOnFragment &&
                connectorContext.materializationMetamodel.pathsByFieldCoordinates
                    .getOrNone(fieldArgumentComponentContext.fieldCoordinates)
                    .flatMap(ImmutableSet<GQLOperationPath>::firstOrNone)
                    .filter { cp: GQLOperationPath ->
                        connectorContext.materializationMetamodel.transformerElementTypePath
                            .isAncestorTo(cp)
                    }
                    .isDefined() -> {
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
                connectorContext
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
                    connectorContext.variableKeys.contains(a.name) ||
                        connectorContext.materializationMetamodel.aliasCoordinatesRegistry
                            .getAllAliasesForFieldArgument(faLoc)
                            .any { n: String -> connectorContext.variableKeys.contains(n) } -> {
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
                    connectorContext.rawInputContextKeys.contains(
                        connectorContext.requestGraph[fieldPath]
                            .toOption()
                            .filterIsInstance<Field>()
                            .map(Field::getName)
                            .orNull()
                    ) -> {
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
                    .put(fieldArgumentComponentContext.path, a)
                    .putEdge(fieldPath, fieldArgumentComponentContext.path, e)
                    .putEdge(
                        fieldArgumentComponentContext.path,
                        connectorContext.materializationMetamodel.dataElementElementTypePath,
                        MaterializationEdge.ELEMENT_TYPE
                    )
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
        return connectorContext
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
            "connect_selected_field: [ selected_field_component_context.path: {} ]",
            selectedFieldComponentContext.path
        )
        val p: GQLOperationPath = selectedFieldComponentContext.path.toUnaliasedPath()
        val elementTypeSegmentNotOnFragment: Boolean =
            p.selection.firstOrNone().filterIsInstance<SelectedField>().isDefined()
        val elementTypeSegmentOnFragment: Boolean = !elementTypeSegmentNotOnFragment
        return when {
            selectedFieldComponentContext.fieldCoordinates in
                connectorContext.materializationMetamodel.elementTypeCoordinates -> {
                connectSelectedElementTypeField(connectorContext, selectedFieldComponentContext)
            }
            elementTypeSegmentNotOnFragment &&
                connectorContext.materializationMetamodel.dataElementElementTypePath.isAncestorTo(
                    p
                ) -> {
                connectSelectedDataElementField(connectorContext, selectedFieldComponentContext)
            }
            elementTypeSegmentNotOnFragment &&
                connectorContext.materializationMetamodel.featureElementTypePath.isAncestorTo(
                    p
                ) -> {
                connectSelectedFeatureField(connectorContext, selectedFieldComponentContext)
            }
            elementTypeSegmentNotOnFragment &&
                connectorContext.materializationMetamodel.transformerElementTypePath.isAncestorTo(
                    p
                ) -> {
                connectSelectedTransformerField(connectorContext, selectedFieldComponentContext)
            }
            elementTypeSegmentOnFragment &&
                connectorContext.materializationMetamodel.pathsByFieldCoordinates
                    .getOrNone(selectedFieldComponentContext.fieldCoordinates)
                    .flatMap(ImmutableSet<GQLOperationPath>::firstOrNone)
                    .filter { cp: GQLOperationPath ->
                        connectorContext.materializationMetamodel.dataElementElementTypePath
                            .isAncestorTo(cp)
                    }
                    .isDefined() -> {
                connectSelectedDataElementField(connectorContext, selectedFieldComponentContext)
            }
            elementTypeSegmentOnFragment &&
                connectorContext.materializationMetamodel.pathsByFieldCoordinates
                    .getOrNone(selectedFieldComponentContext.fieldCoordinates)
                    .flatMap(ImmutableSet<GQLOperationPath>::firstOrNone)
                    .filter { cp: GQLOperationPath ->
                        connectorContext.materializationMetamodel.featureElementTypePath
                            .isAncestorTo(cp)
                    }
                    .isDefined() -> {
                connectSelectedFeatureField(connectorContext, selectedFieldComponentContext)
            }
            elementTypeSegmentOnFragment &&
                connectorContext.materializationMetamodel.pathsByFieldCoordinates
                    .getOrNone(selectedFieldComponentContext.fieldCoordinates)
                    .flatMap(ImmutableSet<GQLOperationPath>::firstOrNone)
                    .filter { cp: GQLOperationPath ->
                        connectorContext.materializationMetamodel.transformerElementTypePath
                            .isAncestorTo(cp)
                    }
                    .isDefined() -> {
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
                    requestGraph(
                        connectorContext.requestGraph
                            .put(
                                selectedFieldComponentContext.path,
                                selectedFieldComponentContext.field
                            )
                            .putEdge(
                                selectedFieldComponentContext.path,
                                GQLOperationPath.getRootPath(),
                                MaterializationEdge.ELEMENT_TYPE
                            )
                    )
                }
            }
            else -> {
                connectorContext
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
                        selectedFieldComponentContext.field
                    )
                )
                .putDataElementCallableBuilderForPath(selectedFieldComponentContext.path, decb)
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
                            .put(
                                selectedFieldComponentContext.path,
                                selectedFieldComponentContext.field
                            )
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
            } else {
                val (domainPath: GQLOperationPath, _: Node<*>) =
                    connectorContext.requestGraph.successorVertices(parentPath).first()
                if (domainPath !in connectorContext.dataElementCallableBuildersByPath) {
                    throw ServiceError.of(
                        "domain_data_element_callable has not been created for [ path: %s ]",
                        domainPath
                    )
                }
                sqb.requestGraph(
                        connectorContext.requestGraph
                            .put(
                                selectedFieldComponentContext.path,
                                selectedFieldComponentContext.field
                            )
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
                connectorContext.featureCalculatorCallableBuildersByPath -> {
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
                connectorContext
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
                        selectedFieldComponentContext.field
                    )
                )
                .putFeatureCalculatorCallableBuilderForPath(
                    selectedFieldComponentContext.path,
                    fcb.addTransformerCallable(tc)
                )
        }
    }

    override fun completeOperationDefinition(connectorContext: StandardQuery): StandardQuery {
        logger.debug(
            "complete_operation_definition: [ connector_context.added_vertex_contexts.size: {} ]",
            connectorContext.addedVertexContexts.size
        )
        return connectorContext
    }
}
