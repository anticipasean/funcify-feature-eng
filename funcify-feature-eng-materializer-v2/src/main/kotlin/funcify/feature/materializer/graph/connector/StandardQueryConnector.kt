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
import funcify.feature.schema.dataelement.DomainSpecifiedDataElementSource
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.toOption
import funcify.feature.tools.extensions.StringExtensions.flatten
import funcify.feature.tools.extensions.TryExtensions.successIfDefined
import graphql.language.Argument
import graphql.language.Field
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
            connectorContext.document
                .getOperationDefinition(connectorContext.operationName)
                .toOption()
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
        val f: Field = selectedFieldComponentContext.field
        val p: GQLOperationPath = selectedFieldComponentContext.path
        return when {
            // Case 1: Already connected
            connectorContext.requestGraph.edgesFromPoint(p).any() ||
                connectorContext.requestGraph.edgesToPoint(p).any() -> {
                connectorContext
            }
            // Case 2: Element Type Path
            p in connectorContext.materializationMetamodel.elementTypePaths -> {
                connectorContext.update { requestGraph(connectorContext.requestGraph.put(p, f)) }
            }
            // Case 3: Domain Path
            p.getParentPath()
                .map { pp: GQLOperationPath ->
                    pp in connectorContext.materializationMetamodel.elementTypePaths
                }
                .isDefined() -> {
                connectorContext.update { requestGraph(connectorContext.requestGraph.put(p, f)) }
            }
            else -> {
                connectorContext
            }
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
