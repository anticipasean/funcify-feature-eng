package funcify.naming.charseq.spliterator

import java.util.Spliterator
import java.util.Spliterators
import java.util.function.Consumer


/**
 *
 * @author smccarron
 * @created 3/22/22
 */
internal class MappingWithIndexSpliterator<T, R>(private val sourceSpliterator: Spliterator<T>,
                                                 private val mapper: (Int, T) -> R,
                                                 private val sizeEstimate: Long = sourceSpliterator.estimateSize(),
                                                 private val additionalCharacteristics: Int = sourceSpliterator.characteristics()) : Spliterators.AbstractSpliterator<R>(sizeEstimate,
                                                                                                                                                                         additionalCharacteristics) {

    private var indexCounter: Int = 0
    private var expended: Boolean = false

    override fun tryAdvance(action: Consumer<in R>?): Boolean {
        if (action == null) {
            return false
        }
        if (expended) {
            return false
        }
        val advanceStatus: Boolean = sourceSpliterator.tryAdvance { t ->
            val currentIndex = indexCounter++
            action.accept(mapper.invoke(currentIndex,
                                        t))
        }
        if (!advanceStatus) {
            expended = true
        }
        return advanceStatus
    }
}