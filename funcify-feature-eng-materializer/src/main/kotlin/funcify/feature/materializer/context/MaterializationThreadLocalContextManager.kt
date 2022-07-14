package funcify.feature.materializer.context

import kotlinx.collections.immutable.ImmutableMap

/**
 *
 * @author smccarron
 * @created 2022-07-14
 */
interface MaterializationThreadLocalContextManager {

    fun startManagedThreadLocalContext(
        contextCapturingFunction: () -> ImmutableMap<ThreadLocalContextKey<*>, Any>,
        contextSettingFunction: (ImmutableMap<ThreadLocalContextKey<*>, Any>) -> Unit
    ): ManagedThreadLocalContext

    fun endManagedThreadLocalContext(managedThreadLocalContext: ManagedThreadLocalContext)
}
