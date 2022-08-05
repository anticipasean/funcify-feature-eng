package funcify.feature.materializer.context

import arrow.core.Option
import arrow.core.none
import java.util.concurrent.atomic.AtomicReference

internal class DefaultThreadLocalContextOperation<O>(
    private val extractor: () -> Option<O>,
    private val setter: (O) -> Unit
) : ThreadLocalContextOperation {

    private val parentContextObjectHolder: AtomicReference<Option<O>> = AtomicReference(none())

    override fun initializeInParentContext() {
        parentContextObjectHolder.compareAndSet(parentContextObjectHolder.get(), extractor.invoke())
    }

    override fun setInChildContext() {
        parentContextObjectHolder.get().tap { o: O -> setter.invoke(o) }
    }
}
