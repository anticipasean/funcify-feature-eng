package funcify.feature.materializer.graph.connector

import funcify.feature.materializer.graph.QueryComponentContext.FieldArgumentComponentContext
import funcify.feature.materializer.graph.QueryComponentContext.FieldComponentContext
import funcify.feature.materializer.graph.QueryComponentContext.FragmentSpreadFieldComponentContext
import funcify.feature.materializer.graph.QueryComponentContext.InlineFragmentFieldComponentContext
import funcify.feature.materializer.graph.QueryComponentContext.OperationDefinitionComponentContext
import funcify.feature.materializer.graph.context.RequestMaterializationGraphContext.RawInputProvidedStandardQuery

/**
 * @author smccarron
 * @created 2023-08-05
 */
object RawInputBasedStandardQueryConnector :
    RawInputProvidedGraphConnector<RawInputProvidedStandardQuery> {
    override fun connectOperationDefinition(
        connectorContext: RawInputProvidedStandardQuery,
        operationDefinitionComponentContext: OperationDefinitionComponentContext,
    ): RawInputProvidedStandardQuery {
        TODO("Not yet implemented")
    }

    override fun connectFieldArgument(
        connectorContext: RawInputProvidedStandardQuery,
        fieldArgumentComponentContext: FieldArgumentComponentContext,
    ): RawInputProvidedStandardQuery {
        TODO("Not yet implemented")
    }

    override fun connectField(
        connectorContext: RawInputProvidedStandardQuery,
        fieldComponentContext: FieldComponentContext,
    ): RawInputProvidedStandardQuery {
        TODO("Not yet implemented")
    }

    override fun connectInlineFragmentField(
        connectorContext: RawInputProvidedStandardQuery,
        inlineFragmentFieldComponentContext: InlineFragmentFieldComponentContext,
    ): RawInputProvidedStandardQuery {
        TODO("Not yet implemented")
    }

    override fun connectFragmentSpreadField(
        connectorContext: RawInputProvidedStandardQuery,
        fragmentSpreadFieldComponentContext: FragmentSpreadFieldComponentContext,
    ): RawInputProvidedStandardQuery {
        TODO("Not yet implemented")
    }
}
