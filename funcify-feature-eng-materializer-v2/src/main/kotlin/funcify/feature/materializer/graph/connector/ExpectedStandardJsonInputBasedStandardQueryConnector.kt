package funcify.feature.materializer.graph.connector

import funcify.feature.materializer.graph.QueryComponentContext.FieldArgumentComponentContext
import funcify.feature.materializer.graph.QueryComponentContext.FieldComponentContext
import funcify.feature.materializer.graph.QueryComponentContext.FragmentSpreadFieldComponentContext
import funcify.feature.materializer.graph.QueryComponentContext.InlineFragmentFieldComponentContext
import funcify.feature.materializer.graph.QueryComponentContext.OperationDefinitionComponentContext
import funcify.feature.materializer.graph.context.RequestMaterializationGraphContext.ExpectedStandardJsonInputStandardQuery

/**
 * @author smccarron
 * @created 2023-08-05
 */
object ExpectedStandardJsonInputBasedStandardQueryConnector :
    ExpectedRawInputBasedGraphConnector<ExpectedStandardJsonInputStandardQuery> {
    override fun connectOperationDefinition(
        connectorContext: ExpectedStandardJsonInputStandardQuery,
        operationDefinitionComponentContext: OperationDefinitionComponentContext,
    ): ExpectedStandardJsonInputStandardQuery {
        TODO("Not yet implemented")
    }

    override fun connectFieldArgument(
        connectorContext: ExpectedStandardJsonInputStandardQuery,
        fieldArgumentComponentContext: FieldArgumentComponentContext,
    ): ExpectedStandardJsonInputStandardQuery {
        TODO("Not yet implemented")
    }

    override fun connectField(
        connectorContext: ExpectedStandardJsonInputStandardQuery,
        fieldComponentContext: FieldComponentContext,
    ): ExpectedStandardJsonInputStandardQuery {
        TODO("Not yet implemented")
    }

    override fun connectInlineFragmentField(
        connectorContext: ExpectedStandardJsonInputStandardQuery,
        inlineFragmentFieldComponentContext: InlineFragmentFieldComponentContext,
    ): ExpectedStandardJsonInputStandardQuery {
        TODO("Not yet implemented")
    }

    override fun connectFragmentSpreadField(
        connectorContext: ExpectedStandardJsonInputStandardQuery,
        fragmentSpreadFieldComponentContext: FragmentSpreadFieldComponentContext,
    ): ExpectedStandardJsonInputStandardQuery {
        TODO("Not yet implemented")
    }
}
