package funcify.feature.naming.charseq.spliterator

import java.util.*
import java.util.Spliterator.NONNULL
import java.util.Spliterator.ORDERED
import java.util.Spliterator.SIZED
import java.util.function.Consumer
import kotlinx.collections.immutable.ImmutableList

/**
 *
 * @author smccarron
 * @created 3/24/22
 */
internal class PairWindowMappingSpliterator<T>(private val inputSpliterator: Spliterator<T>) :
    Spliterator<Pair<T?, T?>> {

    companion object {

        private enum class SourcePosition {
            START,
            MIDDLE,
            END
        }

        private const val DEFAULT_CHARACTERISTICS: Int = ORDERED or NONNULL
    }

    private val sourceWindowSplitr:
        SlidingListWindowMappingSpliterator<T, ImmutableList<T>> by lazy {
        SlidingListWindowMappingSpliterator(
            inputSpliterator = inputSpliterator,
            windowMapper = { wl -> wl },
            windowSize = 2
        )
    }

    private val projectedSize: Long by lazy { sourceWindowSplitr.estimateSize() }

    private val characteristics: Int by lazy {
        DEFAULT_CHARACTERISTICS or sourceWindowSplitr.characteristics()
    }

    private var sourcePosition: SourcePosition = SourcePosition.START

    private var index: Long = 0

    override fun tryAdvance(action: Consumer<in Pair<T?, T?>>?): Boolean {
        if (action == null) {
            return false
        }
        when (sourcePosition) {
            SourcePosition.START -> {
                var pairResult: Pair<T?, T?>? = null
                val advanceStatus: Boolean =
                    sourceWindowSplitr.tryAdvance { windowList ->
                        pairResult =
                            when (windowList.size) {
                                1 -> Pair(null, windowList[0])
                                2 -> Pair(windowList[0], windowList[1])
                                else -> null
                            }
                    }
                if (advanceStatus && pairResult != null) {
                    action.accept(pairResult!!)
                    index++
                    sourcePosition = SourcePosition.MIDDLE
                    return true
                } else {
                    sourcePosition = SourcePosition.END
                    return false
                }
            }
            SourcePosition.MIDDLE -> {
                var pairResult: Pair<T?, T?>? = null
                val advanceStatus: Boolean =
                    sourceWindowSplitr.tryAdvance { windowList ->
                        pairResult =
                            when (windowList.size) {
                                2 -> Pair(windowList[0], windowList[1])
                                1 -> Pair(windowList[0], null)
                                else -> null
                            }
                    }
                if (advanceStatus && pairResult != null) {
                    action.accept(pairResult!!)
                    index++
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

    /**
     * Cannot keep expanding and shrinking partial windows contracts for a given iteration if split
     */
    override fun trySplit(): Spliterator<Pair<T?, T?>>? {
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
