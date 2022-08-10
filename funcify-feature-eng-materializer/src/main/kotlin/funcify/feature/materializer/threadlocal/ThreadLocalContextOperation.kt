package funcify.feature.materializer.threadlocal

/**
 *
 * @author smccarron
 * @created 2022-08-04
 */
interface ThreadLocalContextOperation {

    fun initializeInParentContext(): Unit

    fun setInChildContext(): Unit

    fun unsetChildContext(): Unit

}
