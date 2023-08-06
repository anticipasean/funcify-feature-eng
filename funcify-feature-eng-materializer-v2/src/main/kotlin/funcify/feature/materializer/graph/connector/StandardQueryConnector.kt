package funcify.feature.materializer.graph.connector

import funcify.feature.materializer.graph.QueryComponentContext.FieldArgumentComponentContext
import funcify.feature.materializer.graph.QueryComponentContext.FieldComponentContext
import funcify.feature.materializer.graph.QueryComponentContext.FragmentSpreadFieldComponentContext
import funcify.feature.materializer.graph.QueryComponentContext.InlineFragmentFieldComponentContext
import funcify.feature.materializer.graph.QueryComponentContext.OperationDefinitionComponentContext
import funcify.feature.materializer.graph.context.RequestMaterializationGraphContext.StandardQuery

/**
 * @author smccarron
 * @created 2023-08-05
 */
object StandardQueryConnector : RequestMaterializationGraphConnector<StandardQuery> {

    override fun connectOperationDefinition(
        connectorContext: StandardQuery,
        operationDefinitionComponentContext: OperationDefinitionComponentContext,
    ): StandardQuery {
        TODO("Not yet implemented")
    }

    override fun connectFieldArgument(
        connectorContext: StandardQuery,
        fieldArgumentComponentContext: FieldArgumentComponentContext,
    ): StandardQuery {
        TODO("Not yet implemented")
    }

    override fun connectField(
        connectorContext: StandardQuery,
        fieldComponentContext: FieldComponentContext
    ): StandardQuery {
        TODO("Not yet implemented")
    }

    override fun connectInlineFragmentField(
        connectorContext: StandardQuery,
        inlineFragmentFieldComponentContext: InlineFragmentFieldComponentContext,
    ): StandardQuery {
        TODO("Not yet implemented")
    }

    override fun connectFragmentSpreadField(
        connectorContext: StandardQuery,
        fragmentSpreadFieldComponentContext: FragmentSpreadFieldComponentContext,
    ): StandardQuery {
        TODO("Not yet implemented")
    }
}
