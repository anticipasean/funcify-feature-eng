package funcify.feature.schema.path.result

/**
 * @author smccarron
 * @created 2023-10-19
 */
data class NamedListSegment(val name: String, val index: Int) : ElementSegment {

    init {
        require(name.isNotBlank()) { "name may not be blank" }
        require(index >= 0) { "index must be >= 0" }
    }

    private val internedStringRep: String by lazy {
        buildString {
            append(name)
            append('[')
            append(index)
            append(']')
        }
    }

    override fun toString(): String {
        return internedStringRep
    }
}
