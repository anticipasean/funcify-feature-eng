package funcify.feature.materializer.context

import arrow.core.Option
import arrow.core.none
import java.util.concurrent.atomic.AtomicReference

internal class DefaultThreadLocalContextOperationWithSubject<S, O>(
    private val subject: S,
    private val extractor: (S) -> Option<O>,
    private val setter: (S, O) -> Unit
) : ThreadLocalContextOperation {

    private val parentContextObjectHolder: AtomicReference<Option<O>> = AtomicReference(none())

    override fun initializeInParentContext() {
        parentContextObjectHolder.compareAndSet(
            parentContextObjectHolder.get(),
            extractor.invoke(subject)
        )
    }

    override fun setInChildContext() {
        parentContextObjectHolder.get().tap { o: O -> setter.invoke(subject, o) }
    }
}
