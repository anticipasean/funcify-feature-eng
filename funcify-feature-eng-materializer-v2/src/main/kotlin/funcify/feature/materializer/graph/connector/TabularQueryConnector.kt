package funcify.feature.materializer.graph.connector

import funcify.feature.materializer.graph.QueryComponentContext.FieldArgumentComponentContext
import funcify.feature.materializer.graph.QueryComponentContext.FieldComponentContext
import funcify.feature.materializer.graph.QueryComponentContext.FragmentSpreadFieldComponentContext
import funcify.feature.materializer.graph.QueryComponentContext.InlineFragmentFieldComponentContext
import funcify.feature.materializer.graph.QueryComponentContext.OperationDefinitionComponentContext
import funcify.feature.materializer.graph.context.RequestMaterializationGraphContext.TabularQuery

/**
 * @author smccarron
 * @created 2023-08-05
 */
object TabularQueryConnector : TabularQueryTargetGraphConnector<TabularQuery> {

    override fun connectOperationDefinition(
        connectorContext: TabularQuery,
        operationDefinitionComponentContext: OperationDefinitionComponentContext,
    ): TabularQuery {
        TODO("Not yet implemented")
    }

    override fun connectFieldArgument(
        connectorContext: TabularQuery,
        fieldArgumentComponentContext: FieldArgumentComponentContext,
    ): TabularQuery {
        TODO("Not yet implemented")
    }

    override fun connectField(
        connectorContext: TabularQuery,
        fieldComponentContext: FieldComponentContext
    ): TabularQuery {
        TODO("Not yet implemented")
    }

    override fun connectInlineFragmentField(
        connectorContext: TabularQuery,
        inlineFragmentFieldComponentContext: InlineFragmentFieldComponentContext,
    ): TabularQuery {
        TODO("Not yet implemented")
    }

    override fun connectFragmentSpreadField(
        connectorContext: TabularQuery,
        fragmentSpreadFieldComponentContext: FragmentSpreadFieldComponentContext,
    ): TabularQuery {
        TODO("Not yet implemented")
    }
}
