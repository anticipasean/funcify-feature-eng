package funcify.naming.charseq.spliterator

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import java.util.Spliterator
import java.util.Spliterators
import java.util.function.Consumer


/**
 *
 * @author smccarron
 * @created 3/22/22
 */
internal class SlidingListWindowMappingSpliterator<T, R>(private val inputSpliterator: Spliterator<T>,
                                                         private val incrementSize: Int = 0,
                                                         private val windowSize: Int = 1 + (incrementSize shl 1),
                                                         private val windowMapper: (ImmutableList<T>) -> R,
                                                         private val sizeEstimate: Long = inputSpliterator.estimateSize(),
                                                         private val additionalCharacteristics: Int = inputSpliterator.characteristics()) : Spliterators.AbstractSpliterator<R>(sizeEstimate,
                                                                                                                                                                                additionalCharacteristics) {


    private var slidingListWindow: SlidingListWindow<T> = EmptyWindow(incrementSize = incrementSize.coerceAtLeast((windowSize shr 1) - 1))
    private var expended: Boolean = false

    override fun tryAdvance(action: Consumer<in R>?): Boolean {
        if (action == null) {
            return false
        }
        when (slidingListWindow) {
            /**
             * Case 1: The window is either empty or below the halfway mark in the window -> the increment size
             * so the input spliterator must be advanced forward until the elements to the right of the cursor
             * reach the increment size
             */
            is EmptyWindow<T> -> {
                var windowList = persistentListOf<T>()
                if (!expended) {
                    var advanceStatus: Boolean = false
                    while (windowList.size < slidingListWindow.incrementSize && inputSpliterator.tryAdvance { t ->
                                windowList = windowList.add(t)
                            }
                                    .also { advanceStatus = it }) {

                    }
                    if (!advanceStatus) {
                        expended = true
                    }
                }
                if (windowList.isNotEmpty()) {
                    action.accept(windowMapper.invoke(windowList))
                    slidingListWindow = ForwardOnlyPartialWindow(windowList = windowList,
                                                                 cursorIndex = slidingListWindow.cursorIndex + 1,
                                                                 incrementSize = slidingListWindow.incrementSize,
                                                                 targetWindowSize = slidingListWindow.targetWindowSize)
                    return true
                } else {
                    return false
                }
            }
            /**
             * Case 2: The window is reached the expected half-full point but has not yet reached full size
             * and more elements are expected
             */
            is ForwardOnlyPartialWindow<T> -> {
                var windowList = slidingListWindow.windowList
                if (!expended) {
                    val advanceStatus: Boolean = inputSpliterator.tryAdvance { t ->
                        windowList = windowList.add(t)
                    }
                    if (!advanceStatus) {
                        expended = true
                    }
                }
                if (slidingListWindow.cursorIndex < windowList.size - 1 && slidingListWindow.cursorIndex > 0) {
                    action.accept(windowMapper.invoke(windowList))
                    slidingListWindow = BackwardAndForwardPartialWindow(windowList = windowList,
                                                                        cursorIndex = slidingListWindow.cursorIndex + 1,
                                                                        incrementSize = slidingListWindow.incrementSize,
                                                                        targetWindowSize = slidingListWindow.targetWindowSize)
                    return true
                } else {
                    return false
                }
            }
            is BackwardAndForwardPartialWindow<T> -> {
                var windowList = slidingListWindow.windowList
                if (!expended) {
                    val advanceStatus: Boolean = inputSpliterator.tryAdvance { t ->
                        windowList = windowList.add(t)
                    }
                    if (!advanceStatus) {
                        expended = true
                    }
                }
                if (slidingListWindow.cursorIndex < windowList.size - 1 && slidingListWindow.cursorIndex > slidingListWindow.incrementSize) {
                    action.accept(windowMapper.invoke(windowList))
                    if (windowList.size == slidingListWindow.targetWindowSize) {
                        slidingListWindow = FullWindow(windowList = windowList,
                                                       cursorIndex = slidingListWindow.cursorIndex + 1,
                                                       incrementSize = slidingListWindow.incrementSize,
                                                       targetWindowSize = slidingListWindow.targetWindowSize)
                    } else {
                        slidingListWindow = BackwardAndForwardPartialWindow(windowList = windowList,
                                                                            cursorIndex = slidingListWindow.cursorIndex + 1,
                                                                            incrementSize = slidingListWindow.incrementSize,
                                                                            targetWindowSize = slidingListWindow.targetWindowSize)
                    }
                    return true
                } else {
                    return false
                }
            }
            /**
             * Case 3: The window is full size and each element retrieved from the input should be appended
             * to the window and the oldest or earliest element in the sequence should be removed from
             * the window
             */
            is FullWindow<T> -> {
                var windowList = slidingListWindow.windowList
                if (!expended) {
                    val advanceStatus: Boolean = inputSpliterator.tryAdvance { t ->
                        windowList = windowList.removeAt(0)
                                .add(t)
                    }
                    if (!advanceStatus) {
                        expended = true
                    }
                }
                if (!expended) {
                    action.accept(windowMapper.invoke(windowList))
                    slidingListWindow = FullWindow(windowList = windowList,
                                                   cursorIndex = slidingListWindow.cursorIndex,
                                                   incrementSize = slidingListWindow.incrementSize,
                                                   targetWindowSize = slidingListWindow.targetWindowSize)
                    return true
                } else {
                    val windowListWithoutOldest = slidingListWindow.windowList.removeAt(0)
                    action.accept(windowMapper.invoke(windowListWithoutOldest))
                    slidingListWindow = ShrinkingLastWindow(windowList = windowListWithoutOldest,
                                                            cursorIndex = slidingListWindow.cursorIndex + 1,
                                                            incrementSize = slidingListWindow.incrementSize,
                                                            targetWindowSize = slidingListWindow.targetWindowSize)
                    return true
                }
            }
            /**
             * Case 4: No more elements left in input spliterator but the cursor in the window
             * hasn't reached final index
             */
            is ShrinkingLastWindow<T> -> {
                if (slidingListWindow.windowList.isNotEmpty() && slidingListWindow.cursorIndex < slidingListWindow.targetWindowSize - 1) {
                    val windowListWithoutOldest = slidingListWindow.windowList.removeAt(0)
                    action.accept(windowMapper.invoke(windowListWithoutOldest))
                    slidingListWindow = ShrinkingLastWindow(windowList = windowListWithoutOldest,
                                                            cursorIndex = slidingListWindow.cursorIndex + 1,
                                                            incrementSize = slidingListWindow.incrementSize,
                                                            targetWindowSize = slidingListWindow.targetWindowSize)
                    return true
                } else {
                    return false
                }
            }
            /**
             * Case 5: No more elements left in input spliterator and the cursor in the window has
             * reached the final index
             * => inputSpliteratorExpended && windowCursorIndex >= windowSize
             */
            else -> {
                return false
            }
        }

    }


    companion object {

        private sealed interface SlidingListWindow<T> {
            val windowList: PersistentList<T>
            val cursorIndex: Int
            val incrementSize: Int
            val targetWindowSize: Int
        }

        private data class EmptyWindow<T>(override val incrementSize: Int,
                                          override val windowList: PersistentList<T> = persistentListOf(),
                                          override val cursorIndex: Int = 0,
                                          override val targetWindowSize: Int = 1 + (incrementSize shl 1)) : SlidingListWindow<T> {

        }

        private data class ForwardOnlyPartialWindow<T>(override val windowList: PersistentList<T>,
                                                       override val cursorIndex: Int,
                                                       override val incrementSize: Int,
                                                       override val targetWindowSize: Int) : SlidingListWindow<T> {

        }

        private data class BackwardAndForwardPartialWindow<T>(override val windowList: PersistentList<T>,
                                                              override val cursorIndex: Int,
                                                              override val incrementSize: Int,
                                                              override val targetWindowSize: Int) : SlidingListWindow<T> {

        }

        private data class FullWindow<T>(override val windowList: PersistentList<T>,
                                         override val cursorIndex: Int,
                                         override val incrementSize: Int,
                                         override val targetWindowSize: Int) : SlidingListWindow<T> {

        }

        private data class ShrinkingLastWindow<T>(override val windowList: PersistentList<T>,
                                                  override val cursorIndex: Int,
                                                  override val incrementSize: Int,
                                                  override val targetWindowSize: Int) : SlidingListWindow<T> {

        }
    }
}
