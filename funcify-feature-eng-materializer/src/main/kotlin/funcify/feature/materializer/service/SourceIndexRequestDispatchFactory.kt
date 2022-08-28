package funcify.feature.materializer.service

/**
 *
 * @author smccarron
 * @created 2022-08-28
 */
interface SourceIndexRequestDispatchFactory {

    fun builder(): SourceIndexRequestDispatch.Builder

}
