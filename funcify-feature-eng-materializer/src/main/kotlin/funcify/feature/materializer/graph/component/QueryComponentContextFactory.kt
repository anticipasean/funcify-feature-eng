package funcify.feature.materializer.graph.component

/**
 * @author smccarron
 * @created 2023-08-15
 */
interface QueryComponentContextFactory {

    fun fieldComponentContextBuilder(): QueryComponentContext.FieldComponentContext.Builder

    fun argumentComponentContextBuilder():
        QueryComponentContext.ArgumentComponentContext.Builder
}
