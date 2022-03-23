package funcify.naming.charseq.spliterator

import java.util.Deque
import java.util.LinkedList
import java.util.Spliterator
import java.util.Spliterators
import java.util.function.Consumer


/**
 *
 * @author smccarron
 * @created 3/22/22
 */
internal data class TailFilterSpliterator<T>(private val inputSpliterator: Spliterator<T>,
                                             private val tailFilter: (T) -> Boolean,
                                             private val bufferSupplier: () -> Deque<T> = { LinkedList() },
                                             private val sizeEstimate: Long = inputSpliterator.estimateSize(),
                                             private val additionalCharacteristics: Int = inputSpliterator.characteristics()) : Spliterators.AbstractSpliterator<T>(sizeEstimate,
                                                                                                                                                                    additionalCharacteristics) {
    private val buffer: Deque<T> by lazy { bufferSupplier.invoke() }

    private var expended: Boolean = false

    override fun tryAdvance(action: Consumer<in T>?): Boolean {
        if (action == null) {
            return false
        }
        if (buffer.isNotEmpty()) {
            action.accept(buffer.pollFirst())
            return true
        }
        if (expended) {
            return false
        }
        while (inputSpliterator.tryAdvance { t ->
                    buffer.add(t)
                }) {
            if (buffer.peekLast() == null) {
                buffer.pollLast()
            }
        }
        expended = true
        while (buffer.isNotEmpty() && !tailFilter.invoke(buffer.peekLast())) {
            buffer.pollLast()
        }
        return if (buffer.isNotEmpty()) {
            action.accept(buffer.pollFirst())
            true
        } else {
            false
        }
    }
}