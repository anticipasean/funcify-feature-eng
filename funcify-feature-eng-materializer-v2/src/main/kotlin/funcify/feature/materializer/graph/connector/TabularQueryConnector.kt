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
object TabularQueryConnector : RequestMaterializationGraphConnector<TabularQuery> {

    private val logger: Logger = loggerFor<TabularQueryConnector>()

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
}
