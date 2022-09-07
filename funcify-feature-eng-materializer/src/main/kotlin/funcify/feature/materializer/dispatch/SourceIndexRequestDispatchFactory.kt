package funcify.feature.materializer.dispatch

/**
 *
 * @author smccarron
 * @created 2022-08-28
 */
interface SourceIndexRequestDispatchFactory {

    fun builder(): SourceIndexRequestDispatch.Builder
}
