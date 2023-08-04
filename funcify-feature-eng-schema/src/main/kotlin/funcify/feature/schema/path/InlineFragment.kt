package funcify.feature.schema.path

data class InlineFragment(val typeName: String, val selectedField: SelectedField) : SelectionSegment {

    private val stringForm: String by lazy { "[${typeName}]${selectedField}" }

    override fun toString(): String {
        return stringForm
    }
}
