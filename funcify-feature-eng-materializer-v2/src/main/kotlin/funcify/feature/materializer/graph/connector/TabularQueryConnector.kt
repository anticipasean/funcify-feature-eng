package funcify.feature.materializer.graph.connector

import arrow.core.getOrNone
import arrow.core.orElse
import funcify.feature.error.ServiceError
import funcify.feature.materializer.graph.component.QueryComponentContext.FieldArgumentComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContext.SelectedFieldComponentContext
import funcify.feature.materializer.graph.context.TabularQuery
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.StringExtensions.flatten
import org.slf4j.Logger

/**
 * @author smccarron
 * @created 2023-08-05
 */
object TabularQueryConnector : RequestMaterializationGraphConnector<TabularQuery> {

    private val logger: Logger = loggerFor<TabularQueryConnector>()

    override fun connectOperationDefinition(connectorContext: TabularQuery): TabularQuery {
        logger.info("connect_operation_definition: [ connectorContext. ]")
        when {
            connectorContext.outputColumnNames.isEmpty() -> {
                throw ServiceError.of(
                    """tabular connector applied to context without 
                    |any output column names: 
                    |[ expected: connector_context.output_column_names.isNotEmpty == true, 
                    |actual: . == false ]"""
                        .flatten()
                )
            }
            else -> {
                connectorContext.outputColumnNames.asSequence().map { columnName: String ->
                    connectorContext.materializationMetamodel.featurePathsByName.getOrNone(columnName)
                }
                TODO()
            }
        }
    }

    override fun connectFieldArgument(
        connectorContext: TabularQuery,
        fieldArgumentComponentContext: FieldArgumentComponentContext,
    ): TabularQuery {
        TODO("Not yet implemented")
    }

    override fun connectSelectedField(
        connectorContext: TabularQuery,
        selectedFieldComponentContext: SelectedFieldComponentContext
    ): TabularQuery {
        TODO("Not yet implemented")
    }
}
