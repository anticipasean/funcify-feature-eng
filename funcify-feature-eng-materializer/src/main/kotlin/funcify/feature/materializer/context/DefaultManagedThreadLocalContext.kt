package funcify.feature.materializer.context

import kotlinx.collections.immutable.ImmutableMap

internal data class DefaultManagedThreadLocalContext(
    override val beforeStart: ImmutableMap<ThreadLocalContextKey<*>, Any>,
    override val afterStart: ImmutableMap<ThreadLocalContextKey<*>, Any>
) : ManagedThreadLocalContext {}
