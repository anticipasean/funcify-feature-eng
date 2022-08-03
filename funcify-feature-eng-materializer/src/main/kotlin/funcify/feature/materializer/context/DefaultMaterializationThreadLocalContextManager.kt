package funcify.feature.materializer.context

import funcify.feature.tools.extensions.LoggerExtensions.loggerFor
import kotlinx.collections.immutable.ImmutableMap
import org.slf4j.Logger

/**
 *
 * @author smccarron
 * @created 2022-08-03
 */
internal class DefaultMaterializationThreadLocalContextManager :
    MaterializationThreadLocalContextManager {

    companion object {
        private val logger: Logger = loggerFor<DefaultMaterializationThreadLocalContextManager>()
    }

    override fun startManagedThreadLocalContext(
        contextCapturingFunction: () -> ImmutableMap<ThreadLocalContextKey<*>, Any>,
        contextSettingFunction: (ImmutableMap<ThreadLocalContextKey<*>, Any>) -> Unit,
    ): ManagedThreadLocalContext {
        TODO("Not yet implemented")
    }

    override fun endManagedThreadLocalContext(
        managedThreadLocalContext: ManagedThreadLocalContext
    ) {
        TODO("Not yet implemented")
    }
}
