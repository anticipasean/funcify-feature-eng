package funcify.feature.materializer.graph

import funcify.feature.materializer.graph.component.QueryComponentContext.FieldArgumentComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContext.SelectedFieldComponentContext

/**
 * @author smccarron
 * @created 2023-08-02
 */
interface GraphQLQueryGraphConnector<C> {

    fun startOperationDefinition(connectorContext: C): C

    fun connectFieldArgument(
        connectorContext: C,
        fieldArgumentComponentContext: FieldArgumentComponentContext
    ): C

    fun connectSelectedField(
        connectorContext: C,
        selectedFieldComponentContext: SelectedFieldComponentContext
    ): C

    fun completeOperationDefinition(connectorContext: C): C
}
