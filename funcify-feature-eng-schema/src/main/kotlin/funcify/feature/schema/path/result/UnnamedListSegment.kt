package funcify.feature.schema.path.result

/**
 * @author smccarron
 * @created 2023-10-19
 */
data class UnnamedListSegment(val index: Int) : ElementSegment {

    init {
        require(index >= 0) { "index must be >= 0" }
    }

    private val internedStringRep: String by lazy {
        buildString {
            append('[')
            append(index)
            append(']')
        }
    }

    override fun toString(): String {
        return internedStringRep
    }
}
