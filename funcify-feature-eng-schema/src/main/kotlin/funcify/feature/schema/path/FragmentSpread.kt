package funcify.feature.schema.path

data class FragmentSpread(
    val fragmentName: String,
    val typeName: String,
    val selectedField: SelectedField
) : SelectionSegment {

    private val stringForm: String by lazy { "[${fragmentName}:${typeName}]${selectedField}" }

    override fun toString(): String {
        return stringForm
    }
}
