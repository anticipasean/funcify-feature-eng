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
                                                         private val windowMapper: (ImmutableList<T>) -> R,
                                                         private val windowSize: Int = 1,
                                                         private val sizeEstimate: Long = inputSpliterator.estimateSize(),
                                                         private val additionalCharacteristics: Int = inputSpliterator.characteristics()) : Spliterators.AbstractSpliterator<R>(sizeEstimate,
                                                                                                                                                                                additionalCharacteristics) {

    private val targetWindowSize: Int = windowSize.coerceAtLeast(1)

    /**
     * ## increment_size:
     * ### Definition:
     * - the number of elements that can be viewed as "occurring after / succeeding" a given element from a source sequence
     * within a given window of size _n_
     *
     * ### Examples:
     * #### Given sequence([ 1, 2, 3, 4, 5 ]) : the first window of size 3 => [ _ , 1, 2 ]
     * - One "null" value, represented by "_", can be said to have 2 elements succeeding it: 1, 2
     * - A 3 element window on the first element ( [0]: 1 ) of the sequence would be: <null>, 1, 2
     * - The increment_size for a 3 element window on any element in the sequence would thus be 2
     *
     * #### Given sequence([ 1, 2, 3, 4, 5 ]) : the first window of size 2 => [ _, 1 ]
     * - One "null" values can be said to have 1 element succeeding it: 1
     * - A 2 element window on the first element ( [0]: 1 ) of the sequence would be: <null>, 1
     * - The increment_size for a 2 element window on any element in the sequence would thus be 1
     *
     * #### Given sequence([ 1, 2, 3, 4, 5 ]) : the first window of size 4 => [ _, _, 1, 2 ]
     * - Two "null" values can be said to have 2 element succeeding them: 1, 2
     * - A 4 element window on the first element ( [0]: 1 ) of the sequence would be: <null>, <null>, 1, 2
     * - The increment_size for a 4 element window on any element in the sequence would thus be 2
     */
    private val incrementSize: Int = if ((targetWindowSize and 1) == 0) {
        (targetWindowSize shr 1).coerceAtLeast(0)
    } else {
        (targetWindowSize shr 1).coerceAtLeast(0) + 1
    }
    private var window: PersistentList<T> = persistentListOf()
    private var windowState: WindowState = WindowState.NEW

    override fun tryAdvance(action: Consumer<in R>?): Boolean {
        if (action == null) {
            return false
        }
        when (windowState) {
            /**
             * Case 1: The window is new so the input spliterator must be advanced forward
             * until the window reaches the increment_size
             */
            WindowState.NEW -> {
                var windowList = persistentListOf<T>()
                var advanceStatus: Boolean = false
                while (windowList.size < incrementSize && inputSpliterator.tryAdvance { t ->
                            windowList = windowList.add(t)
                        }
                                .also { advanceStatus = it }) {

                }
                if (advanceStatus) {
                    action.accept(windowMapper.invoke(windowList))
                    window = windowList
                    if (windowList.size == targetWindowSize) {
                        windowState = WindowState.FULL
                    } else {
                        windowState = WindowState.EXPANDING_PARTIAL
                    }
                    return true
                } else {
                    if (windowList.isNotEmpty()) {
                        action.accept(windowMapper.invoke(windowList))
                        window = windowList
                        windowState = WindowState.SHRINKING_PARTIAL
                        return true
                    } else {
                        windowState = WindowState.CLOSED
                        return false
                    }
                }
            }
            /**
             * Case 2: The window has reached the increment_size but has not yet reached
             * _full_ target size and more elements are expected
             */
            WindowState.EXPANDING_PARTIAL -> {
                var windowList = window
                val advanceStatus: Boolean = inputSpliterator.tryAdvance { t ->
                    windowList = windowList.add(t)
                }
                if (advanceStatus) {
                    action.accept(windowMapper.invoke(windowList))
                    if (windowList.size == targetWindowSize) {
                        window = windowList
                        windowState = WindowState.FULL
                    } else {
                        window = windowList
                        windowState = WindowState.EXPANDING_PARTIAL
                    }
                    return true
                } else {
                    if (windowList.isNotEmpty()) {
                        action.accept(windowMapper.invoke(windowList))
                        window = windowList
                        windowState = WindowState.SHRINKING_PARTIAL
                        return true
                    } else {
                        windowState = WindowState.CLOSED
                        return false
                    }
                }
            }
            /**
             * Case 3: The window is _full_ size and each element retrieved from the input should be appended
             * to the window and the oldest or earliest element in the window should be removed
             */
            WindowState.FULL -> {
                var windowList = window
                val advanceStatus: Boolean = inputSpliterator.tryAdvance { t ->
                    windowList = windowList.add(t)
                }
                if (windowList.size > targetWindowSize) {
                    windowList = windowList.removeAt(0)
                }
                if (advanceStatus) {
                    action.accept(windowMapper.invoke(windowList))
                    window = windowList
                    windowState = WindowState.FULL
                } else {
                    if (windowList.size > incrementSize) {
                        val windowWithOldestRemoved = windowList.removeAt(0)
                        action.accept(windowMapper.invoke(windowWithOldestRemoved))
                        window = windowWithOldestRemoved
                        windowState = WindowState.SHRINKING_PARTIAL
                    } else {
                        window = persistentListOf()
                        windowState = WindowState.CLOSED
                        return false
                    }
                }
                return true
            }
            /**
             * Case 4: No more elements are left in input spliterator but there are remaining windows
             * to take action on before indicating the spliterator is closed
             */
            WindowState.SHRINKING_PARTIAL -> {
                val windowList = window
                if (windowList.size > incrementSize) {
                    val windowListWithoutOldest = windowList.removeAt(0)
                    action.accept(windowMapper.invoke(windowListWithoutOldest))
                    window = windowListWithoutOldest
                    windowState = WindowState.SHRINKING_PARTIAL
                    return true
                } else {
                    window = persistentListOf()
                    windowState = WindowState.CLOSED
                    return false
                }
            }
            /**
             * Case 5: There are not any remaining windows of values to take action on
             */
            WindowState.CLOSED -> {
                return false
            }
        }

    }

    companion object {

        private enum class WindowState {
            NEW,
            EXPANDING_PARTIAL,
            FULL,
            SHRINKING_PARTIAL,
            CLOSED
        }

    }
}

