package funcify.feature.materializer.graph.connector

import arrow.core.Option
import arrow.core.firstOrNone
import arrow.core.getOrNone
import arrow.core.orElse
import funcify.feature.error.ServiceError
import funcify.feature.materializer.graph.component.QueryComponentContext.FieldArgumentComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContext.SelectedFieldComponentContext
import funcify.feature.materializer.graph.context.TabularQuery
import funcify.feature.schema.path.operation.GQLOperationPath
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.SequenceExtensions.firstOrNone
import funcify.feature.tools.extensions.StringExtensions.flatten
import graphql.language.Field
import graphql.schema.FieldCoordinates
import kotlinx.collections.immutable.ImmutableSet
import org.slf4j.Logger

/**
 * @author smccarron
 * @created 2023-08-05
 */
object TabularQueryConnector : RequestMaterializationGraphConnector<TabularQuery> {

    private val logger: Logger = loggerFor<TabularQueryConnector>()

    override fun startOperationDefinition(connectorContext: TabularQuery): TabularQuery {
        logger.info(
            "connect_operation_definition: [ connectorContext.addedVertices.size: {} ]",
            connectorContext.addedVertexContexts.size
        )
        return when {
            connectorContext.outputColumnNames.isEmpty() -> {
                throw ServiceError.of(
                    """tabular connector applied to context without 
                    |any output column names: 
                    |[ expected: connector_context.output_column_names.isNotEmpty == true, 
                    |actual: . == false ]"""
                        .flatten()
                )
            }
            connectorContext.rawInputContextKeys.isEmpty() -> {
                connectorContext.update {
                    addedVertexContexts(
                        TabularQueryVariableBasedOperationCreator.invoke(
                            tabularQuery = connectorContext
                        )
                    )
                }
            }
            else -> {
                // TODO: Can move scope of update call to outside fold, prompting only one update
                // call per fold
                connectorContext.outputColumnNames.asSequence().fold(connectorContext) {
                    c: TabularQuery,
                    columnName: String ->
                    // Assumption: Feature names are unique within the features namespace so start
                    // there
                    val featurePathAndField: Option<Pair<GQLOperationPath, Field>> =
                        connectorContext.materializationMetamodel.featurePathsByName
                            .getOrNone(columnName)
                            .map { p: GQLOperationPath ->
                                p to Field.newField().name(columnName).build()
                            }
                            .orElse {
                                // Assumption: Features have only one set of field_coordinates
                                connectorContext.materializationMetamodel.aliasCoordinatesRegistry
                                    .getFieldsWithAlias(columnName)
                                    .firstOrNone()
                                    .flatMap { fc: FieldCoordinates ->
                                        connectorContext.materializationMetamodel
                                            .pathsByFieldCoordinates
                                            .getOrNone(fc)
                                            .fold(
                                                ::emptySequence,
                                                ImmutableSet<GQLOperationPath>::asSequence
                                            )
                                            .firstOrNone { p: GQLOperationPath ->
                                                connectorContext.materializationMetamodel
                                                    .featureSpecifiedFeatureCalculatorsByPath
                                                    .containsKey(p)
                                            }
                                            .map { p: GQLOperationPath ->
                                                p.transform {
                                                    dropTailSelectionSegment()
                                                    appendAliasedField(columnName, fc.fieldName)
                                                } to
                                                    Field.newField()
                                                        .name(fc.fieldName)
                                                        .alias(columnName)
                                                        .build()
                                            }
                                    }
                            }
                    when {
                        featurePathAndField.isDefined() -> {
                            c
                        }
                        columnName in c.rawInputContextKeys || columnName in c.variableKeys -> {
                            c.update { addPassThroughColumn(columnName) }
                        }
                        else -> {
                            c.update { addUnhandledColumnName(columnName) }
                        }
                    }
                }
            }
        }
    }

    override fun connectFieldArgument(
        connectorContext: TabularQuery,
        fieldArgumentComponentContext: FieldArgumentComponentContext,
    ): TabularQuery {
        logger.debug("connect_field_argument: [ path: {} ]", fieldArgumentComponentContext.path)
        return connectorContext
    }

    override fun connectSelectedField(
        connectorContext: TabularQuery,
        selectedFieldComponentContext: SelectedFieldComponentContext
    ): TabularQuery {
        logger.debug("connect_selected_field: [ path: {} ]", selectedFieldComponentContext.path)
        return connectorContext
    }

    override fun completeOperationDefinition(connectorContext: TabularQuery): TabularQuery {
        return when {
            connectorContext.unhandledOutputColumnNames.isNotEmpty() -> {
                throw ServiceError.of(
                    "unhandled_output_columns still present in context: [ columns: %s ]",
                    connectorContext.unhandledOutputColumnNames.asSequence().joinToString(", ")
                )
            }
            else -> {
                connectorContext
            }
        }
    }
}
