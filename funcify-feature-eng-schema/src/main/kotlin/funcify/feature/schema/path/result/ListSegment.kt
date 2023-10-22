package funcify.feature.schema.path.result

import kotlinx.collections.immutable.PersistentList

/**
 * @author smccarron
 * @created 2023-10-19
 */
data class ListSegment(val name: String, val indices: PersistentList<Int>) : ElementSegment {

    init {
        require(indices.size >= 1) { "indices must contain at least one index" }
        require(indices.all { i: Int -> i >= 0 }) { "all indices must be >= 0" }
    }

    private val internedStringRep: String by lazy {
        buildString {
            append(name)
            this@ListSegment.indices.asSequence().forEach { i: Int ->
                append('[')
                append(i)
                append(']')
            }
        }
    }

    override fun toString(): String {
        return internedStringRep
    }
}
