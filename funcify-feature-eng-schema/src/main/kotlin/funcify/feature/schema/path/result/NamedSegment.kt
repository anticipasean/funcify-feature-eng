package funcify.feature.schema.path.result

/**
 * @author smccarron
 * @created 2023-10-19
 */
data class NamedSegment(val name: String) : ElementSegment {

    init {
        require(name.isNotBlank()) { "name may not be blank" }
    }

    override fun toString(): String {
        return name
    }
}
