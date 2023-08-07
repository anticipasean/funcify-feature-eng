package funcify.feature.schema.path.operation

data class AliasedFieldSegment(val alias: String, override val fieldName: String) :
    SelectionSegment, SelectedField {

    private val stringForm: String by lazy { "$alias:$fieldName" }

    override fun toString(): String {
        return stringForm
    }
}
