package funcify.feature.naming.charseq.spliterator

import kotlinx.collections.immutable.ImmutableList
import java.util.Spliterator
import java.util.Spliterator.NONNULL
import java.util.Spliterator.ORDERED
import java.util.Spliterator.SIZED
import java.util.function.Consumer


/**
 *
 * @author smccarron
 * @created 3/24/22
 */
internal class TripleWindowMappingSpliterator<T>(private val inputSpliterator: Spliterator<T>) : Spliterator<Triple<T?, T, T?>> {

    companion object {

        private enum class SourcePosition {
            START,
            MIDDLE,
            END
        }

        private const val DEFAULT_CHARACTERISTICS: Int = ORDERED and NONNULL

    }

    private val sourceWindowSplitr: SlidingListWindowMappingSpliterator<T, ImmutableList<T>> by lazy {
        SlidingListWindowMappingSpliterator(inputSpliterator = inputSpliterator,
                                            windowMapper = { wl -> wl },
                                            windowSize = 3)
    }

    private val projectedSize: Long by lazy {
        sourceWindowSplitr.estimateSize()
    }

    private val characteristics: Int by lazy {
        DEFAULT_CHARACTERISTICS or sourceWindowSplitr.characteristics()
    }

    private var sourcePosition: SourcePosition = SourcePosition.START

    private var index: Long = 0

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
                    index++
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
                    index++
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

    /**
     * Cannot keep expanding and shrinking partial windows contracts
     * for a given iteration if split
     */
    override fun trySplit(): Spliterator<Triple<T?, T, T?>>? {
        return null
    }

    override fun estimateSize(): Long {
        return if ((characteristics and SIZED) == SIZED) {
            projectedSize - index
        } else {
            Long.MAX_VALUE
        }
    }

    override fun characteristics(): Int {
        return characteristics
    }
}