package funcify.feature.materializer.graph.connector

import funcify.feature.materializer.graph.QueryComponentContext
import funcify.feature.materializer.graph.context.RequestMaterializationGraphContext.ExpectedTabularInputTabularQuery

/**
 * @author smccarron
 * @created 2023-08-05
 */
object ExpectedTabularInputBasedTabularQueryConnector :
    ExpectedRawInputBasedGraphConnector<ExpectedTabularInputTabularQuery>,
    TabularQueryTargetGraphConnector<ExpectedTabularInputTabularQuery> {
    override fun connectOperationDefinition(
        connectorContext: ExpectedTabularInputTabularQuery,
        operationDefinitionComponentContext:
            QueryComponentContext.OperationDefinitionComponentContext,
    ): ExpectedTabularInputTabularQuery {
        TODO("Not yet implemented")
    }

    override fun connectFieldArgument(
        connectorContext: ExpectedTabularInputTabularQuery,
        fieldArgumentComponentContext: QueryComponentContext.FieldArgumentComponentContext,
    ): ExpectedTabularInputTabularQuery {
        TODO("Not yet implemented")
    }

    override fun connectField(
        connectorContext: ExpectedTabularInputTabularQuery,
        fieldComponentContext: QueryComponentContext.FieldComponentContext,
    ): ExpectedTabularInputTabularQuery {
        TODO("Not yet implemented")
    }

    override fun connectInlineFragmentField(
        connectorContext: ExpectedTabularInputTabularQuery,
        inlineFragmentFieldComponentContext:
            QueryComponentContext.InlineFragmentFieldComponentContext,
    ): ExpectedTabularInputTabularQuery {
        TODO("Not yet implemented")
    }

    override fun connectFragmentSpreadField(
        connectorContext: ExpectedTabularInputTabularQuery,
        fragmentSpreadFieldComponentContext:
            QueryComponentContext.FragmentSpreadFieldComponentContext,
    ): ExpectedTabularInputTabularQuery {
        TODO("Not yet implemented")
    }
}
