package funcify.feature.schema.path

data class InlineFragment(val typeName: String, override val fieldName: String) : SelectionSegment {

    private val stringForm: String by lazy { "[${typeName}]${fieldName}" }

    override fun toString(): String {
        return stringForm
    }
}
