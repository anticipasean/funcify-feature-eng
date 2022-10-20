package funcify.feature.materializer.context

/**
 *
 * @author smccarron
 * @created 2022-10-12
 */
interface MaterializationGraphContextFactory {

    fun builder(): MaterializationGraphContext.Builder

}
