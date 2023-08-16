package funcify.feature.materializer.graph.connector

import funcify.feature.materializer.graph.component.QueryComponentContext.FieldArgumentComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContext.SelectedFieldComponentContext
import funcify.feature.materializer.graph.context.TabularQuery
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import org.slf4j.Logger

/**
 * @author smccarron
 * @created 2023-08-05
 */
object TabularQueryConnector : TabularQueryTargetGraphConnector<TabularQuery> {

    private val logger: Logger = loggerFor<TabularQueryConnector>()

    override fun connectOperationDefinition(connectorContext: TabularQuery): TabularQuery {
        logger.info("connect_operation_definition: [ ]")
        TODO("Not yet implemented")
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
