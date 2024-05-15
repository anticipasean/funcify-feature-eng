package funcify.feature.materializer.graph

import funcify.feature.materializer.graph.component.QueryComponentContext.ArgumentComponentContext
import funcify.feature.materializer.graph.component.QueryComponentContext.FieldComponentContext

/**
 * @author smccarron
 * @created 2023-08-02
 */
interface GraphQLQueryGraphConnector<C> {

    fun connectArgument(
        connectorContext: C,
        argumentComponentContext: ArgumentComponentContext
    ): C

    fun connectField(
        connectorContext: C,
        fieldComponentContext: FieldComponentContext
    ): C
}
