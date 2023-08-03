package funcify.feature.schema.path

data class Field(override val fieldName: String) : SelectionSegment {

    override fun toString(): String {
        return fieldName
    }
}
