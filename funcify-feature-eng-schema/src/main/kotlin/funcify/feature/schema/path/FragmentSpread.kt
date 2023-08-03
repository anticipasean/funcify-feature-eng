package funcify.feature.schema.path

data class FragmentSpread(
    val fragmentName: String,
    val typeName: String,
    override val fieldName: String
) : SelectionSegment {

    private val stringForm: String by lazy { "[${fragmentName}:${typeName}]${fieldName}" }

    override fun toString(): String {
        return stringForm
    }
}
