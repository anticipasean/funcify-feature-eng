package funcify.naming.charseq.spliterator

import java.util.Spliterator
import java.util.Spliterator.NONNULL
import java.util.Spliterator.ORDERED
import java.util.Spliterator.SIZED
import java.util.Spliterator.SUBSIZED
import java.util.function.Consumer


/**
 *
 * @author smccarron
 * @created 3/22/22
 */
internal class MappingWithIndexSpliterator<T, R>(private val sourceSpliterator: Spliterator<T>,
                                                 private val mapper: (Int, T) -> R,
                                                 private var index: Int = 0,
                                                 private val exclusiveLimit: Int = determineDefaultExclusiveLimit(sourceSpliterator),
                                                 private val characteristics: Int = DEFAULT_CHARACTERISTICS or sourceSpliterator.characteristics()) : Spliterator<R> {

    companion object {
        const val DEFAULT_CHARACTERISTICS: Int = ORDERED and NONNULL

        private fun isSizedPerCharacteristics(characteristics: Int): Boolean {
            return (((characteristics and SIZED) == SIZED || (characteristics and SUBSIZED) == SUBSIZED))
        }

        private fun <T> determineDefaultExclusiveLimit(sourceSpliterator: Spliterator<T>): Int {
            return if (isSizedPerCharacteristics(sourceSpliterator.characteristics())) {
                when (val estimatedSize: Long = sourceSpliterator.estimateSize()) {
                    Long.MAX_VALUE -> {
                        Int.MAX_VALUE
                    }
                    else -> {
                        estimatedSize.toInt()
                    }
                }
            } else {
                Int.MAX_VALUE
            }
        }
    }

    private var expended: Boolean = false

    private fun cannotAccessSourceWithGivenParameters(): Boolean {
        return expended || (isSizedPerCharacteristics(characteristics) && exclusiveLimit <= 0 || index < 0 || index >= exclusiveLimit)
    }

    override fun tryAdvance(action: Consumer<in R>?): Boolean {
        if (action == null || cannotAccessSourceWithGivenParameters()) {
            return false
        }
        val advanceStatus: Boolean = sourceSpliterator.tryAdvance { t ->
            action.accept(mapper.invoke(index++,
                                        t))
        }
        if (!advanceStatus) {
            expended = true
        }
        return advanceStatus
    }

    override fun trySplit(): Spliterator<R>? {
        if (cannotAccessSourceWithGivenParameters()) {
            return null
        }
        if (isSizedPerCharacteristics(characteristics)) {
            val split: Spliterator<T>? = sourceSpliterator.trySplit()
            if (split == null) {
                return null
            } else {
                val lo = index
                val mid = lo + exclusiveLimit ushr 1
                return if (lo >= mid) {
                    null
                } else {
                    MappingWithIndexSpliterator(sourceSpliterator = split,
                                                mapper = mapper,
                                                index = lo,
                                                exclusiveLimit = mid.also { index = it },
                                                characteristics = characteristics)
                }
            }
        }
        return null
    }


    override fun estimateSize(): Long {
        if (cannotAccessSourceWithGivenParameters()) {
            return 0L
        }
        if (exclusiveLimit == Int.MAX_VALUE) {
            return Long.MAX_VALUE
        }
        return (exclusiveLimit - index).toLong()
    }

    override fun characteristics(): Int {
        return characteristics
    }
}

