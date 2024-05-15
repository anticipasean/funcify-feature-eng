package funcify.feature.schema.path.operation

data class FragmentSpreadSegment(
    val fragmentName: String,
    val typeName: String,
    val selectedField: SelectedField
) : SelectionSegment {

    private val stringForm: String by lazy { "[${fragmentName}:${typeName}]${selectedField}" }

    override fun toString(): String {
        return stringForm
    }
}
