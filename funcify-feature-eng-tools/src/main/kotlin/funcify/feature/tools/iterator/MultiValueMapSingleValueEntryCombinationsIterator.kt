package funcify.feature.tools.iterator

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

/** Leverages algorithm used in com.google.common.collect.CartesianList */
internal class MultiValueMapSingleValueEntryCombinationsIterator<K, V>(
    private val inputMultiValueMap: Map<K, List<V>>
) : Iterator<Map<K, V>> {

    private val keysList: List<K> by lazy { inputMultiValueMap.keys.toList() }

    private val valueSizeProducts: IntArray by lazy {
        IntArray(keysList.size + 1).also { ia: IntArray ->
            ia[keysList.size] = 1
            (keysList.size - 1).downTo(0).asSequence().forEach { i: Int ->
                ia[i] = ia[i + 1] * inputMultiValueMap[keysList[i]]!!.size
            }
        }
    }

    private val combinationCount: Int by lazy {
        when {
            inputMultiValueMap.isNotEmpty() -> {
                valueSizeProducts[0]
            }
            else -> {
                0
            }
        }
    }

    private var combinationIndex: Int = 0

    override fun hasNext(): Boolean {
        // println(
        //    "state: [ combination_count: %s, value_size_products: %s ]".format(
        //        combinationCount,
        //        Arrays.toString(valueSizeProducts)
        //    )
        // )
        return combinationIndex < combinationCount
    }

    override fun next(): Map<K, V> {
        if (combinationIndex >= combinationCount) {
            throw NoSuchElementException("no more combinations")
        }
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
        return result.build()
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
}
