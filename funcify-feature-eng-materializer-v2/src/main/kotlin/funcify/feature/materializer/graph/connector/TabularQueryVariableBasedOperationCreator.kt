package funcify.feature.materializer.graph.connector

import funcify.feature.materializer.graph.component.QueryComponentContext
import funcify.feature.materializer.graph.context.TabularQuery
import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import org.slf4j.Logger

object TabularQueryVariableBasedOperationCreator :
    (TabularQuery) -> Iterable<QueryComponentContext> {

    private const val METHOD_TAG: String = "tabular_query_variable_based_operation_creator.invoke"
    private val logger: Logger = loggerFor<TabularQueryVariableBasedOperationCreator>()

    override fun invoke(tabularQuery: TabularQuery): Iterable<QueryComponentContext> {
        logger.info(
            "{}: [ tabular_query.variable_keys.size: {} ]",
            METHOD_TAG,
            tabularQuery.variableKeys.size
        )

        TODO("Not yet implemented")
    }
}
