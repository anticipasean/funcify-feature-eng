package funcify.feature.materializer.graph.connector

import funcify.feature.materializer.graph.QueryComponentContext.FieldArgumentComponentContext
import funcify.feature.materializer.graph.QueryComponentContext.FieldComponentContext
import funcify.feature.materializer.graph.QueryComponentContext.FragmentSpreadFieldComponentContext
import funcify.feature.materializer.graph.QueryComponentContext.InlineFragmentFieldComponentContext
import funcify.feature.materializer.graph.QueryComponentContext.OperationDefinitionComponentContext
import funcify.feature.materializer.graph.context.RequestMaterializationGraphContext.RawInputProvidedTabularQuery

/**
 * @author smccarron
 * @created 2023-08-05
 */
object RawInputBasedTabularQueryConnector :
    RawInputProvidedGraphConnector<RawInputProvidedTabularQuery>,
    TabularQueryTargetGraphConnector<RawInputProvidedTabularQuery> {

    override fun connectOperationDefinition(
        connectorContext: RawInputProvidedTabularQuery,
        operationDefinitionComponentContext: OperationDefinitionComponentContext,
    ): RawInputProvidedTabularQuery {
        TODO("Not yet implemented")
    }

    override fun connectFieldArgument(
        connectorContext: RawInputProvidedTabularQuery,
        fieldArgumentComponentContext: FieldArgumentComponentContext,
    ): RawInputProvidedTabularQuery {
        TODO("Not yet implemented")
    }

    override fun connectField(
        connectorContext: RawInputProvidedTabularQuery,
        fieldComponentContext: FieldComponentContext,
    ): RawInputProvidedTabularQuery {
        TODO("Not yet implemented")
    }

    override fun connectInlineFragmentField(
        connectorContext: RawInputProvidedTabularQuery,
        inlineFragmentFieldComponentContext: InlineFragmentFieldComponentContext,
    ): RawInputProvidedTabularQuery {
        TODO("Not yet implemented")
    }

    override fun connectFragmentSpreadField(
        connectorContext: RawInputProvidedTabularQuery,
        fragmentSpreadFieldComponentContext: FragmentSpreadFieldComponentContext,
    ): RawInputProvidedTabularQuery {
        TODO("Not yet implemented")
    }
}
