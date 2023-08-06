package funcify.feature.materializer.graph

import funcify.feature.materializer.graph.QueryComponentContext.FieldArgumentComponentContext
import funcify.feature.materializer.graph.QueryComponentContext.FieldComponentContext
import funcify.feature.materializer.graph.QueryComponentContext.FragmentSpreadFieldComponentContext
import funcify.feature.materializer.graph.QueryComponentContext.InlineFragmentFieldComponentContext
import funcify.feature.materializer.graph.QueryComponentContext.OperationDefinitionComponentContext

/**
 * @author smccarron
 * @created 2023-08-02
 */
interface GraphQLQueryGraphConnector<C> {

    fun connectOperationDefinition(
        connectorContext: C,
        operationDefinitionComponentContext: OperationDefinitionComponentContext
    ): C

    fun connectFieldArgument(
        connectorContext: C,
        fieldArgumentComponentContext: FieldArgumentComponentContext
    ): C

    fun connectField(connectorContext: C, fieldComponentContext: FieldComponentContext): C

    fun connectInlineFragmentField(
        connectorContext: C,
        inlineFragmentFieldComponentContext: InlineFragmentFieldComponentContext
    ): C

    fun connectFragmentSpreadField(
        connectorContext: C,
        fragmentSpreadFieldComponentContext: FragmentSpreadFieldComponentContext
    ): C
}
