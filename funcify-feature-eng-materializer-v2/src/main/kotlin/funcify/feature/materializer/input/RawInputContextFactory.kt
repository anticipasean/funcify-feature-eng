package funcify.feature.materializer.input

/**
 * @author smccarron
 * @created 2023-07-30
 */
interface RawInputContextFactory {

    companion object {
        fun defaultFactory(): RawInputContextFactory {
            return DefaultRawInputContextFactory()
        }
    }

    fun builder(): RawInputContext.Builder
}
