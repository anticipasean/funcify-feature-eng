package funcify.feature.schema.path

data class AliasedField(val alias: String, override val fieldName: String) :
    SelectionSegment, SelectedField {

    private val stringForm: String by lazy { "$alias:$fieldName" }

    override fun toString(): String {
        return stringForm
    }
}
