package funcify.feature.materializer.threadlocal

import kotlinx.collections.immutable.ImmutableMap

internal data class DefaultManagedThreadLocalContext(
    override val beforeStart: ImmutableMap<ThreadLocalContextKey<*>, Any>,
    override val afterStart: ImmutableMap<ThreadLocalContextKey<*>, Any>
) : ManagedThreadLocalContext {}
