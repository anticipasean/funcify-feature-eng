package funcify.feature.naming.charseq.spliterator

import java.util.Deque
import java.util.LinkedList
import java.util.Spliterator
import java.util.function.Consumer


/**
 *
 * @author smccarron
 * @created 3/19/22
 */
internal class DuplicatingSpliterator<T>(private val inputSpliterator: Spliterator<T>,
                                         private val bufferSupplier: () -> Deque<T> = { LinkedList<T>() },
                                         private val inputCharacteristics: Int = inputSpliterator.characteristics()) : Spliterator<T> {

    private val parentBuffer: Deque<T> by lazy(bufferSupplier)
    private val childBuffers: MutableList<Deque<T>> by lazy { LinkedList() }
    private val hasNextFunction: () -> Boolean by lazy {
        createHasNextFunctionOnInputSpliterator(inputSpliterator,
                                                parentBuffer,
                                                childBuffers)
    }

    companion object {

        private fun <T> createHasNextFunctionOnInputSpliterator(inputSpliterator: Spliterator<T>,
                                                                parentBuffer: Deque<T>,
                                                                childBuffers: List<Deque<T>>): () -> Boolean {
            return {
                inputSpliterator.tryAdvance { t ->
                    parentBuffer.offer(t)
                    childBuffers.forEach { buff ->
                        buff.offer(t)
                    }
                }
            }
        }

        private class ChildDuplicateSpliterator<T>(private val childBuffer: Deque<T>,
                                                   private val hasNextFunction: () -> Boolean,
                                                   private val sizeEstimate: Long,
                                                   private val inputCharacteristics: Int) : Spliterator<T> {

            override fun tryAdvance(action: Consumer<in T>?): Boolean {
                if (action == null) {
                    return false
                }
                return if (childBuffer.isNotEmpty() || hasNextFunction.invoke()) {
                    val next = childBuffer.poll()
                    if (next == null) {
                        false
                    } else {
                        action.accept(next)
                        true
                    }
                } else {
                    false
                }
            }

            override fun trySplit(): Spliterator<T>? {
                return null
            }

            override fun estimateSize(): Long {
                return sizeEstimate
            }

            override fun characteristics(): Int {
                return inputCharacteristics
            }

        }

    }

    fun duplicate(): Spliterator<T> {
        val newChildBuffer = bufferSupplier.invoke()
        childBuffers.add(newChildBuffer)
        return ChildDuplicateSpliterator(newChildBuffer,
                                         hasNextFunction,
                                         inputSpliterator.estimateSize(),
                                         inputCharacteristics)
    }


    override fun tryAdvance(action: Consumer<in T>?): Boolean {
        if (action == null) {
            return false
        }
        if (parentBuffer.isNotEmpty() || hasNextFunction.invoke()) {
            val next = parentBuffer.poll()
            return if (next == null) {
                false
            } else {
                action.accept(next)
                true
            }
        }
        return false
    }

    /**
     * The contract with duplicates would break if split within this context:
     * - [ A, B, C, D ] => parentSplitr: DuplicateSpliterator
     * - parentSplitr.duplicate() => parentSplitr [ A, B, C, D ]  and childSplitr [ A, B, C, D ]
     * - parentSplitr.trySplit() => parentSplitr1 [ A, B ] and parentSplitr2 [ C, D ] and childSplitr [ C, D ] ?!?!
     * - an implementation of parentSplitr.trySplit() would need to take the inputSpliterator and uses its trySplit call which typically,
     *   if implemented on that inputSpliterator, would return a _new_ instance of Spliterator that references the values in
     *   the first half of the given source index range and keep the other half in the inputSpliterator instance
     * - the childSplitr gets its values from the tryAdvance calls on the parent instance it was created from
     * - thus, the childSplitr that was supposed to be a "duplicate" of the original parentSplitr would end up only receiving
     *   the latter half of the values from the inputSpliterator instance
     *
     * Splits or parallelization should be done within a different context: upstream (before creating the duplicatingspliterator) or
     * downstream (on the output of the duplicatingspliterator or its children)
     *
     * @return null since cannot split since contract could not be upheld on splits
     */
    override fun trySplit(): Spliterator<T>? {
        return null
    }

    override fun estimateSize(): Long {
        return inputSpliterator.estimateSize()
    }

    override fun characteristics(): Int {
        return inputCharacteristics
    }

}