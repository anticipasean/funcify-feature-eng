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
internal class TripleWindowMappingSpliterator<T>(private val inputSpliterator: Spliterator<T>,
                                                 private val sizeEstimate: Long = inputSpliterator.estimateSize(),
                                                 private val additionalCharacteristics: Int = inputSpliterator.characteristics()) : Spliterators.AbstractSpliterator<Triple<T?, T, T?>>(sizeEstimate,
                                                                                                                                                                                        additionalCharacteristics) {
    private val sourceWindowSplitr: SlidingListWindowMappingSpliterator<T, ImmutableList<T>> by lazy {
        SlidingListWindowMappingSpliterator(inputSpliterator = inputSpliterator,
                                            windowMapper = { wl -> wl },
                                            windowSize = 3)
    }

    private var sourcePosition: SourcePosition = SourcePosition.START

    override fun tryAdvance(action: Consumer<in Triple<T?, T, T?>>?): Boolean {
        if (action == null) {
            return false
        }
        when (sourcePosition) {
            SourcePosition.START -> {
                var tripleResult: Triple<T?, T, T?>? = null
                val advanceStatus: Boolean = sourceWindowSplitr.tryAdvance { windowList ->
                    tripleResult = when (windowList.size) {
                        1 -> Triple(null,
                                    windowList[0],
                                    null)
                        2 -> Triple(null,
                                    windowList[0],
                                    windowList[1])
                        3 -> Triple(windowList[0],
                                    windowList[1],
                                    windowList[2])
                        else -> null
                    }
                }
                if (advanceStatus && tripleResult != null) {
                    action.accept(tripleResult!!)
                    if (tripleResult!!.third == null) {
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
            SourcePosition.MIDDLE -> {
                var tripleResult: Triple<T?, T, T?>? = null
                val advanceStatus: Boolean = sourceWindowSplitr.tryAdvance { windowList ->
                    tripleResult = when (windowList.size) {
                        3 -> Triple(windowList[0],
                                    windowList[1],
                                    windowList[2])
                        2 -> Triple(windowList[0],
                                    windowList[1],
                                    null)
                        else -> null
                    }
                }
                if (advanceStatus && tripleResult != null) {
                    action.accept(tripleResult!!)
                    if (tripleResult!!.third == null) {
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