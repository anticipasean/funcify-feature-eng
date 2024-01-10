package funcify.feature.materializer.dispatch.context

/**
 * @author smccarron
 * @created 2023-09-18
 */
interface DispatchedRequestMaterializationGraphContextFactory {

    fun builder(): DispatchedRequestMaterializationGraphContext.Builder

}
