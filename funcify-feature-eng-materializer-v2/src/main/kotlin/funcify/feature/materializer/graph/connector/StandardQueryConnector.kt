package funcify.feature.materializer.graph.connector

import arrow.core.filterIsInstance
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
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.schema.transformer.TransformerSpecifiedTransformerSource
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.language.Argument
import graphql.language.Field
import graphql.language.Node
import graphql.language.OperationDefinition
import graphql.language.Value
import graphql.language.VariableReference
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLArgument
import kotlinx.collections.immutable.ImmutableMap
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
        return when {
            /*
             * [graphql.language.Document.getOperationDefinition] does not handle _null_ or empty
             * names as expected
             *
             * => filter for operation_name only if it's not blank, else use the first found
             */
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
                .isEmpty() -> {
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
                        .fold(this) { sqb: StandardQuery.Builder, qcc: QueryComponentContext ->
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
        val a: Argument = fieldArgumentComponentContext.argument
        val p: GQLOperationPath = fieldArgumentComponentContext.path
        return when {
            // Case 1: Already connected
            connectorContext.requestGraph.edgesFromPoint(p).any() ||
                connectorContext.requestGraph.edgesToPoint(p).any() -> {
                connectorContext
            }
            // Case 2: Domain Data Element Argument
            p.getParentPath()
                .flatMap(GQLOperationPath::getParentPath)
                .map { gp: GQLOperationPath ->
                    gp == connectorContext.materializationMetamodel.dataElementElementTypePath
                }
                .getOrElse { false } -> {
                val fieldPath: GQLOperationPath =
                    p.getParentPath()
                        .filter { pp: GQLOperationPath ->
                            connectorContext.requestGraph.contains(pp)
                        }
                        .successIfDefined {
                            ServiceError.of(
                                """domain data_element source field has not 
                                        |been defined in request_materialization_graph 
                                        |for [ path: %s ]"""
                                    .flatten(),
                                p.getParentPath().orNull()
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
                                p
                            )
                        }
                    }
                connectorContext.update {
                    requestGraph(connectorContext.requestGraph.put(p, a).putEdge(fieldPath, p, e))
                }
            }
            else -> {
                connectorContext
            }
        }
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
        return when {
            connectorContext.materializationMetamodel.elementTypePaths.contains(p) -> {
                connectElementTypeSelectedField(connectorContext, selectedFieldComponentContext)
            }
            connectorContext.materializationMetamodel.dataElementElementTypePath.isAncestorTo(
                p
            ) -> {
                connectSelectedDataElementField(connectorContext, selectedFieldComponentContext)
            }
            connectorContext.materializationMetamodel.transformerElementTypePath.isAncestorTo(
                p
            ) -> {
                connectSelectedTransformerField(connectorContext, selectedFieldComponentContext)
            }
            connectorContext.materializationMetamodel.featureElementTypePath.isAncestorTo(p) -> {
                connectSelectedFeatureField(connectorContext, selectedFieldComponentContext)
            }
            else -> {
                throw ServiceError.of("unhandled path type for selected_field [ path: %s ]", p)
            }
        }
    }

    private fun connectElementTypeSelectedField(
        connectorContext: StandardQuery,
        selectedFieldComponentContext: SelectedFieldComponentContext,
    ): StandardQuery {
        logger.debug(
            "connect_element_type_selected_field: [ selected_field_component_context.path: {} ]",
            selectedFieldComponentContext.path
        )
        val p: GQLOperationPath = selectedFieldComponentContext.path.toUnaliasedPath()
        return when {
            p == connectorContext.materializationMetamodel.dataElementElementTypePath &&
                connectorContext.materializationMetamodel.dataElementElementTypePath !in
                    connectorContext.requestGraph -> {
                // Case 1: Connect data_element_element_type_path to root if not already connected
                connectorContext.update {
                    requestGraph(
                        connectorContext.requestGraph.put(
                            selectedFieldComponentContext.path,
                            selectedFieldComponentContext.field
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
        val p: GQLOperationPath = selectedFieldComponentContext.path.toUnaliasedPath()
        return when {
            p in
                connectorContext.materializationMetamodel
                    .domainSpecifiedDataElementSourceByPath -> {
                connectorContext.update(
                    connectDomainDataElement(connectorContext, selectedFieldComponentContext)
                )
            }
            else -> {
                connectorContext.update(
                    connectSelectedDataElementToDomainDataElement(
                        connectorContext,
                        selectedFieldComponentContext
                    )
                )
            }
        }
    }

    private fun connectDomainDataElement(
        connectorContext: StandardQuery,
        selectedFieldComponentContext: SelectedFieldComponentContext
    ): (StandardQuery.Builder) -> StandardQuery.Builder {
        return { sqb: StandardQuery.Builder ->
            val p: GQLOperationPath = selectedFieldComponentContext.path.toUnaliasedPath()
            val decb: DataElementCallable.Builder =
                connectorContext.materializationMetamodel.domainSpecifiedDataElementSourceByPath
                    .getOrNone(p)
                    .successIfDefined {
                        ServiceError.of(
                            "domain_specified_data_element_source not available at [ path: %s ]",
                            p
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
                    connectorContext.requestGraph.put(p, selectedFieldComponentContext.field)
                )
                .putDataElementCallableBuilderForPath(p, decb)
        }
    }

    private fun connectSelectedDataElementToDomainDataElement(
        connectorContext: StandardQuery,
        selectedFieldComponentContext: SelectedFieldComponentContext,
    ): (StandardQuery.Builder) -> StandardQuery.Builder {
        return { sqb: StandardQuery.Builder ->
            val p: GQLOperationPath = selectedFieldComponentContext.path.toUnaliasedPath()
            val parentPath: GQLOperationPath =
                p.getParentPath()
                    .successIfDefined {
                        ServiceError.of(
                            "selected_data_element should not have root path [ field.name: %s ]",
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
                    |data_element fields: [ parent.path: %s ]"""
                        .flatten(),
                    parentPath
                )
            }
            if (parentPath in connectorContext.dataElementCallableBuildersByPath) {
                sqb.requestGraph(
                        connectorContext.requestGraph
                            .put(p, selectedFieldComponentContext.field)
                            .putEdge(p, parentPath, MaterializationEdge.EXTRACT_FROM_SOURCE)
                    )
                    .putDataElementCallableBuilderForPath(
                        parentPath,
                        connectorContext.dataElementCallableBuildersByPath
                            .get(parentPath)!!
                            .selectPathWithinDomain(p)
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
                            .put(p, selectedFieldComponentContext.field)
                            .putEdge(p, domainPath, MaterializationEdge.EXTRACT_FROM_SOURCE)
                    )
                    .putDataElementCallableBuilderForPath(
                        domainPath,
                        connectorContext.dataElementCallableBuildersByPath[domainPath]!!
                            .selectPathWithinDomain(p)
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
        val p: GQLOperationPath = selectedFieldComponentContext.path.toUnaliasedPath()
        return when {
            p in connectorContext.transformerCallablesByPath -> {
                connectorContext
            }
            p in
                connectorContext.materializationMetamodel
                    .transformerSpecifiedTransformerSourcesByPath ||
                selectedFieldComponentContext.fieldCoordinates in
                    connectorContext.materializationMetamodel
                        .transformerSpecifiedTransformerSourcesByCoordinates -> {
                connectorContext.update(
                    connectTransformer(connectorContext, selectedFieldComponentContext)
                )
            }
            else -> {
                connectorContext
            }
        }
    }

    private fun connectTransformer(
        connectorContext: StandardQuery,
        selectedFieldComponentContext: SelectedFieldComponentContext
    ): (StandardQuery.Builder) -> StandardQuery.Builder {
        return { sqb: StandardQuery.Builder ->
            val p: GQLOperationPath = selectedFieldComponentContext.path.toUnaliasedPath()
            if (
                p !in
                    connectorContext.materializationMetamodel
                        .transformerSpecifiedTransformerSourcesByPath
            ) {
                throw ServiceError.of(
                    "transformer_specified_transformer_source not available for [ (unaliased) path: %s ]",
                    p
                )
            }
            val tsts: TransformerSpecifiedTransformerSource =
                connectorContext.materializationMetamodel
                    .transformerSpecifiedTransformerSourcesByPath
                    .get(p)!!
            sqb.putTransformerCallableForPath(
                p,
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
        return connectorContext
    }

    override fun completeOperationDefinition(connectorContext: StandardQuery): StandardQuery {
        logger.debug(
            "complete_operation_definition: [ connector_context.added_vertex_contexts.size: {} ]",
            connectorContext.addedVertexContexts.size
        )
        return connectorContext
    }
}
