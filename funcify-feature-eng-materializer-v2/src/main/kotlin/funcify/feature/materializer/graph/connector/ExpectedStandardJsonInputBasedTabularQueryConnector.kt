package funcify.feature.materializer.graph.connector

import funcify.feature.materializer.graph.component.QueryComponentContext.FieldArgumentComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContext.SelectedFieldComponentContext
import funcify.feature.materializer.graph.context.ExpectedStandardJsonInputTabularQuery
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import org.slf4j.Logger

/**
 * @author smccarron
 * @created 2023-08-05
 */
object ExpectedStandardJsonInputBasedTabularQueryConnector :
    ExpectedRawInputBasedGraphConnector<ExpectedStandardJsonInputTabularQuery> {

    private val logger: Logger = loggerFor<ExpectedStandardJsonInputBasedTabularQueryConnector>()

    override fun connectOperationDefinition(
        connectorContext: ExpectedStandardJsonInputTabularQuery
    ): ExpectedStandardJsonInputTabularQuery {
        TODO("Not yet implemented")
    }

    override fun connectFieldArgument(
        connectorContext: ExpectedStandardJsonInputTabularQuery,
        fieldArgumentComponentContext: FieldArgumentComponentContext,
    ): ExpectedStandardJsonInputTabularQuery {
        TODO("Not yet implemented")
    }

    override fun connectSelectedField(
        connectorContext: ExpectedStandardJsonInputTabularQuery,
        selectedFieldComponentContext: SelectedFieldComponentContext,
    ): ExpectedStandardJsonInputTabularQuery {
        TODO("Not yet implemented")
    }
}
