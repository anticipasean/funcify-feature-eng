package funcify.feature.materializer.graph.connector

import funcify.feature.materializer.graph.component.QueryComponentContext.FieldArgumentComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContext.SelectedFieldComponentContext
import funcify.feature.materializer.graph.context.StandardQuery
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import org.slf4j.Logger

/**
 * @author smccarron
 * @created 2023-08-05
 */
object StandardQueryConnector : RequestMaterializationGraphConnector<StandardQuery> {

    private val logger: Logger = loggerFor<StandardQueryConnector>()

    override fun connectOperationDefinition(connectorContext: StandardQuery): StandardQuery {
        TODO("Not yet implemented")
    }

    override fun connectFieldArgument(
        connectorContext: StandardQuery,
        fieldArgumentComponentContext: FieldArgumentComponentContext,
    ): StandardQuery {
        TODO("Not yet implemented")
    }

    override fun connectSelectedField(
        connectorContext: StandardQuery,
        selectedFieldComponentContext: SelectedFieldComponentContext
    ): StandardQuery {
        TODO("Not yet implemented")
    }
}
