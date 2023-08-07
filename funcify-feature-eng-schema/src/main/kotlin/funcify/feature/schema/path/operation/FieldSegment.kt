package funcify.feature.schema.path.operation

data class FieldSegment(override val fieldName: String) : SelectionSegment, SelectedField {

    override fun toString(): String {
        return fieldName
    }
}
