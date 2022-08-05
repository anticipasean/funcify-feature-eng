package funcify.feature.materializer.context

/**
 *
 * @author smccarron
 * @created 2022-08-04
 */
interface ThreadLocalContextOperation {

    fun initializeInParentContext(): Unit

    fun setInChildContext(): Unit

}
