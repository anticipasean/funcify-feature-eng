package funcify.feature.materializer.threadlocal

import arrow.core.Option
import arrow.core.none
import java.util.concurrent.atomic.AtomicReference

internal class DefaultThreadLocalContextOperation<O>(
    private val extractor: () -> Option<O>,
    private val setter: (O) -> Unit,
    private val unsetter: (O) -> Unit
) : ThreadLocalContextOperation {

    private val parentContextObjectHolder: AtomicReference<Option<O>> = AtomicReference(none())

    override fun initializeInParentContext() {
        parentContextObjectHolder.compareAndSet(parentContextObjectHolder.get(), extractor.invoke())
    }

    override fun setInChildContext() {
        parentContextObjectHolder.get().tap { o: O -> setter.invoke(o) }
    }

    override fun unsetChildContext() {
        parentContextObjectHolder.get().tap { o: O -> unsetter.invoke(o) }
    }
}
