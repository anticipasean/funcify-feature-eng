package funcify.naming.charseq.spliterator

import kotlinx.collections.immutable.ImmutableList
import java.util.Spliterator
import java.util.Spliterators
import java.util.function.Consumer


/**
 *
 * @author smccarron
 * @created 3/24/22
 */
internal class PairWindowMappingSpliterator<T>(private val inputSpliterator: Spliterator<T>,
                                               private val sizeEstimate: Long = inputSpliterator.estimateSize(),
                                               private val additionalCharacteristics: Int = inputSpliterator.characteristics()) : Spliterators.AbstractSpliterator<Pair<T?, T?>>(sizeEstimate,
                                                                                                                                                                                 additionalCharacteristics) {
    private val sourceWindowSplitr: SlidingListWindowMappingSpliterator<T, ImmutableList<T>> by lazy {
        SlidingListWindowMappingSpliterator(inputSpliterator = inputSpliterator,
                                            windowMapper = { wl -> wl },
                                            windowSize = 2)
    }

    private var sourcePosition: SourcePosition = SourcePosition.START

    override fun tryAdvance(action: Consumer<in Pair<T?, T?>>?): Boolean {
        if (action == null) {
            return false
        }
        when (sourcePosition) {
            SourcePosition.START -> {
                var pairResult: Pair<T?, T?>? = null
                val advanceStatus: Boolean = sourceWindowSplitr.tryAdvance { windowList ->
                    pairResult = when (windowList.size) {
                        1 -> Pair(null,
                                  windowList[0])
                        2 -> Pair(windowList[0],
                                  windowList[1])
                        else -> null
                    }
                }
                if (advanceStatus && pairResult != null) {
                    action.accept(pairResult!!)
                    sourcePosition = SourcePosition.MIDDLE
                    return true
                } else {
                    sourcePosition = SourcePosition.END
                    return false
                }
            }
            SourcePosition.MIDDLE -> {
                var pairResult: Pair<T?, T?>? = null
                val advanceStatus: Boolean = sourceWindowSplitr.tryAdvance { windowList ->
                    pairResult = when (windowList.size) {
                        2 -> Pair(windowList[0],
                                  windowList[1])
                        1 -> Pair(windowList[0],
                                  null)
                        else -> null
                    }
                }
                if (advanceStatus && pairResult != null) {
                    action.accept(pairResult!!)
                    if (pairResult!!.second == null) {
                        sourcePosition = SourcePosition.END
                    } else {
                        sourcePosition = SourcePosition.MIDDLE
                    }
                    return true
                } else {
                    sourcePosition = SourcePosition.END
                    return false
                }
            }
            SourcePosition.END -> {
                return false
            }
        }
    }

    companion object {

        private enum class SourcePosition {
            START,
            MIDDLE,
            END
        }

    }

}