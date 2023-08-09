package funcify.feature.materializer.dispatch

/**
 *
 * @author smccarron
 * @created 2023-08-08
 */
interface DispatchedRequestMaterializationGraphFactory {

    fun builder(): DispatchedRequestMaterializationGraph.Builder

}
