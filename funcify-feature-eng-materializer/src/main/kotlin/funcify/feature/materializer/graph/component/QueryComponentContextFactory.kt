package funcify.feature.materializer.graph.component

/**
 * @author smccarron
 * @created 2023-08-15
 */
interface QueryComponentContextFactory {

    fun selectedFieldComponentContextBuilder():
        QueryComponentContext.SelectedFieldComponentContext.Builder

    fun fieldArgumentComponentContextBuilder():
        QueryComponentContext.FieldArgumentComponentContext.Builder
}
