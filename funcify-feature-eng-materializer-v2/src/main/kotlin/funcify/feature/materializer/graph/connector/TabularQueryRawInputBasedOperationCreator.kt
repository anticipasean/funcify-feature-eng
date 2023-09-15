package funcify.feature.materializer.graph.connector

import funcify.feature.materializer.graph.component.QueryComponentContext
import funcify.feature.materializer.graph.context.TabularQuery

internal object TabularQueryRawInputBasedOperationCreator :
    (TabularQuery) -> Iterable<QueryComponentContext> {

    override fun invoke(tabularQuery: TabularQuery): Iterable<QueryComponentContext> {
        TODO("Not yet implemented")
    }
}
