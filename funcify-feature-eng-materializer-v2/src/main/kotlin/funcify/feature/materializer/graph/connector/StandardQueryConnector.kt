package funcify.feature.materializer.graph.connector

import funcify.feature.error.ServiceError
import funcify.feature.materializer.graph.component.QueryComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContext.FieldArgumentComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContext.SelectedFieldComponentContext
import funcify.feature.materializer.graph.context.StandardQuery
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import funcify.feature.tools.extensions.OptionExtensions.toOption
import org.slf4j.Logger

/**
 * @author smccarron
 * @created 2023-08-05
 */
object StandardQueryConnector : RequestMaterializationGraphConnector<StandardQuery> {

    private val logger: Logger = loggerFor<StandardQueryConnector>()

    override fun startOperationDefinition(connectorContext: StandardQuery): StandardQuery {
        logger.debug("connect_operation_definition: [ connectorContext. ]")
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
                StandardQueryTraverser(
                        queryComponentContextFactory = connectorContext.queryComponentContextFactory
                    )
                    .invoke(
                        operationName = connectorContext.operationName,
                        document = connectorContext.document
                    )
                    .fold(connectorContext) { c: StandardQuery, qcc: QueryComponentContext ->
                        c.update { addVertexContext(qcc) }
                    }
            }
        }
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

    override fun completeOperationDefinition(connectorContext: StandardQuery): StandardQuery {
        TODO("Not yet implemented")
    }
}
