package funcify.feature.materializer.graph.connector

import funcify.feature.materializer.graph.component.QueryComponentContext.FieldArgumentComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContext.SelectedFieldComponentContext
import funcify.feature.materializer.graph.context.ExpectedTabularInputStandardQuery
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import org.slf4j.Logger

/**
 * @author smccarron
 * @created 2023-08-05
 */
object ExpectedTabularInputBasedStandardQueryConnector :
    ExpectedRawInputBasedGraphConnector<ExpectedTabularInputStandardQuery> {

    private val logger: Logger = loggerFor<ExpectedTabularInputBasedStandardQueryConnector>()

    override fun connectOperationDefinition(
        connectorContext: ExpectedTabularInputStandardQuery
    ): ExpectedTabularInputStandardQuery {
        TODO("Not yet implemented")
    }

    override fun connectFieldArgument(
        connectorContext: ExpectedTabularInputStandardQuery,
        fieldArgumentComponentContext: FieldArgumentComponentContext,
    ): ExpectedTabularInputStandardQuery {
        TODO("Not yet implemented")
    }

    override fun connectSelectedField(
        connectorContext: ExpectedTabularInputStandardQuery,
        selectedFieldComponentContext: SelectedFieldComponentContext,
    ): ExpectedTabularInputStandardQuery {
        TODO("Not yet implemented")
    }
}
