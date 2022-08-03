package funcify.feature.materializer.context

import arrow.core.or
import kotlinx.collections.immutable.ImmutableMap
import reactor.util.context.ContextView

/**
 *
 * @author smccarron
 * @created 2022-07-14
 */
interface ManagedThreadLocalContext {

    companion object {

        private val REACTOR_CONTEXT_VIEW_KEY: ThreadLocalContextKey<ContextView> =
            ThreadLocalContextKey.of(
                ManagedThreadLocalContext::class.qualifiedName + ".REACTOR_CONTEXT_VIEW"
            )

        private val THREAD_ID_KEY: ThreadLocalContextKey<Int> =
            ThreadLocalContextKey.of(ManagedThreadLocalContext::class.qualifiedName + ".THREAD_ID")

        private val THREAD_LOCAL_VALUES_KEY: ThreadLocalContextKey<Map<String, Any>> =
            ThreadLocalContextKey.of(
                ManagedThreadLocalContext::class.qualifiedName + ".THREAD_LOCAL_VALUES"
            )
    }

    val threadId: Int
        get() =
            THREAD_ID_KEY.filterValue(beforeStart[THREAD_ID_KEY])
                .or(THREAD_ID_KEY.filterValue(afterStart[THREAD_ID_KEY]))
                .orNull()
                ?: -1

    val beforeStart: ImmutableMap<ThreadLocalContextKey<*>, Any>

    val afterStart: ImmutableMap<ThreadLocalContextKey<*>, Any>
}
