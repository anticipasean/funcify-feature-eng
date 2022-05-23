package funcify.feature.naming.charseq.spliterator

import java.util.*
import java.util.function.Consumer

/**
 *
 * @author smccarron
 * @created 3/22/22
 */
internal data class TailOperationSpliterator<T>(
    private val inputSpliterator: Spliterator<T>,
    private val tailOperator: (T) -> T,
    private val sizeEstimate: Long = inputSpliterator.estimateSize(),
    private val additionalCharacteristics: Int = inputSpliterator.characteristics()
) : Spliterators.AbstractSpliterator<T>(sizeEstimate, additionalCharacteristics) {
    private val buffer: Deque<T> by lazy { LinkedList() }
    private var expended: Boolean = false

    override fun tryAdvance(action: Consumer<in T>?): Boolean {
        if (action == null) {
            return false
        }
        if (expended && buffer.isEmpty()) {
            return false
        }
        if (expended && buffer.isNotEmpty()) {
            action.accept(tailOperator.invoke(buffer.pollFirst()))
            return true
        }
        val advanceStatus: Boolean = inputSpliterator.tryAdvance { t -> buffer.offerLast(t) }
        if (!advanceStatus) {
            expended = true
        }
        when (buffer.size) {
            2 -> {
                action.accept(buffer.pollFirst())
                return true
            }
            1 -> {
                val nextAdvanceStatus: Boolean =
                    inputSpliterator.tryAdvance { t -> buffer.offerLast(t) }
                if (!nextAdvanceStatus) {
                    expended = true
                }
                action.accept(buffer.pollFirst())
                return true
            }
            else -> {
                return false
            }
        }
    }
}
