package funcify.feature.tools.spliterator

import java.util.*
import java.util.function.Consumer
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

/** Leverages algorithm used in com.google.common.collect.CartesianList */
internal class MultiValueMapSingleValueEntryCombinationsSpliterator<K, V>(
    private val inputMultiValueMap: Map<K, List<V>>,
    private val keysList: List<K> = inputMultiValueMap.keys.toList(),
    private val valueSizeProducts: IntArray =
        createValueSizeProductsArrayFromMultiValueMap(inputMultiValueMap, keysList),
    private val combinationCountLimit: Int =
        calculateTotalCombinationCount(inputMultiValueMap, valueSizeProducts),
    private var combinationIndex: Int = 0,
) : Spliterator<Map<K, V>> {

    companion object {

        private const val DEFAULT_CHARACTERISTICS: Int =
            Spliterator.SIZED or Spliterator.NONNULL or Spliterator.SUBSIZED

        private fun <K, V> createValueSizeProductsArrayFromMultiValueMap(
            inputMultiValueMap: Map<K, List<V>>,
            keysList: List<K>
        ): IntArray {
            return when {
                keysList.size != inputMultiValueMap.keys.size -> {
                    throw IllegalArgumentException(
                        "keys_list.size does not match input_multi_value_map.keys.size"
                    )
                }
                else -> {
                    IntArray(keysList.size + 1).also { ia: IntArray ->
                        ia[keysList.size] = 1
                        (keysList.size - 1).downTo(0).asSequence().forEach { i: Int ->
                            ia[i] = ia[i + 1] * inputMultiValueMap[keysList[i]]!!.size
                        }
                    }
                }
            }
        }

        private fun <K, V> calculateTotalCombinationCount(
            inputMultiValueMap: Map<K, List<V>>,
            valueSizeProducts: IntArray
        ): Int {
            return when {
                inputMultiValueMap.keys.size + 1 != valueSizeProducts.size -> {
                    throw IllegalArgumentException(
                        "input_multi_value_map.keys.size + 1 does not match value_size_products.size"
                    )
                }
                inputMultiValueMap.isNotEmpty() -> {
                    valueSizeProducts[0]
                }
                else -> {
                    0
                }
            }
        }
    }

    init {
        when {
            combinationIndex < 0 -> {
                throw IllegalArgumentException("combination_index may not be less than zero")
            }
            combinationCountLimit < 0 -> {
                throw IllegalArgumentException("combination_count_limit may not be less than zero")
            }
            combinationIndex > combinationCountLimit -> {
                throw IllegalArgumentException(
                    "combination_index may not be greater than combination_count_limit"
                )
            }
            keysList.size != inputMultiValueMap.keys.size -> {
                throw IllegalArgumentException(
                    "keys_list.size does not match input_multi_value_map.keys.size"
                )
            }
            inputMultiValueMap.keys.size + 1 != valueSizeProducts.size -> {
                throw IllegalArgumentException(
                    "input_multi_value_map.keys.size + 1 does not match value_size_products.size"
                )
            }
        }
    }

    override fun tryAdvance(action: Consumer<in Map<K, V>>?): Boolean {
        return when {
            action == null || combinationIndex >= combinationCountLimit -> {
                false
            }
            else -> {
                val result: PersistentMap.Builder<K, V> = persistentMapOf<K, V>().builder()
                var key: K? = null
                for (keyIndex: Int in 0 until keysList.size) {
                    key = keysList[keyIndex]
                    when (val vs: List<V>? = inputMultiValueMap.get(key)) {
                        null -> {
                            throw IllegalStateException(
                                "key [ %s ] expected but not found in backing map".format(key)
                            )
                        }
                        else -> {
                            result[key] =
                                vs.get(
                                    getValueIndexForKeyAndItsIndexAtCombinationIndex(
                                        key,
                                        keyIndex,
                                        combinationIndex
                                    )
                                )
                        }
                    }
                }
                combinationIndex++
                action.accept(result.build())
                true
            }
        }
    }

    /**
     * Should not be called if _any_ list value of a key-value pair is empty, i.e.
     * `inputMultiValueMap[key].size == 0`
     */
    private fun getValueIndexForKeyAndItsIndexAtCombinationIndex(
        key: K,
        keyIndex: Int,
        combinationIndex: Int
    ): Int {
        return (combinationIndex / valueSizeProducts[keyIndex + 1]) % inputMultiValueMap[key]!!.size
    }

    override fun trySplit(): Spliterator<Map<K, V>>? {
        return if (combinationIndex < combinationCountLimit) {
            val lo = combinationIndex
            val mid = lo + (combinationCountLimit ushr 1)
            return if (lo >= mid) {
                null
            } else {
                MultiValueMapSingleValueEntryCombinationsSpliterator<K, V>(
                    inputMultiValueMap = inputMultiValueMap,
                    keysList = keysList,
                    valueSizeProducts = valueSizeProducts,
                    combinationIndex = lo,
                    combinationCountLimit = mid.also { combinationIndex = mid }
                )
            }
        } else {
            null
        }
    }

    override fun estimateSize(): Long {
        return (combinationCountLimit - combinationIndex).toLong()
    }

    override fun getExactSizeIfKnown(): Long {
        return (combinationCountLimit - combinationIndex).toLong()
    }

    override fun characteristics(): Int {
        return DEFAULT_CHARACTERISTICS
    }
}
