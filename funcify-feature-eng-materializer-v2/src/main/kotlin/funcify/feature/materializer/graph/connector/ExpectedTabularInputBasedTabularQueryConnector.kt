package funcify.feature.materializer.graph.connector

import funcify.feature.materializer.graph.component.QueryComponentContext
import funcify.feature.materializer.graph.context.ExpectedTabularInputTabularQuery
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import org.slf4j.Logger

/**
 * @author smccarron
 * @created 2023-08-05
 */
object ExpectedTabularInputBasedTabularQueryConnector :
    ExpectedRawInputBasedGraphConnector<ExpectedTabularInputTabularQuery>,
    TabularQueryTargetGraphConnector<ExpectedTabularInputTabularQuery> {

    private val logger: Logger = loggerFor<ExpectedTabularInputBasedTabularQueryConnector>()

    override fun connectOperationDefinition(
        connectorContext: ExpectedTabularInputTabularQuery
    ): ExpectedTabularInputTabularQuery {
        TODO("Not yet implemented")
    }

    override fun connectFieldArgument(
        connectorContext: ExpectedTabularInputTabularQuery,
        fieldArgumentComponentContext: QueryComponentContext.FieldArgumentComponentContext,
    ): ExpectedTabularInputTabularQuery {
        TODO("Not yet implemented")
    }

    override fun connectSelectedField(
        connectorContext: ExpectedTabularInputTabularQuery,
        selectedFieldComponentContext: QueryComponentContext.SelectedFieldComponentContext,
    ): ExpectedTabularInputTabularQuery {
        TODO("Not yet implemented")
    }
}
