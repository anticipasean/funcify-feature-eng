package funcify.feature.materializer.input.context

/**
 * @author smccarron
 * @created 2023-07-30
 */
interface RawInputContextFactory {

    fun builder(): RawInputContext.Builder

}
